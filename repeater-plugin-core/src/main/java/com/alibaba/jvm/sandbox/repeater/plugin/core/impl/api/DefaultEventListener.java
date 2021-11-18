package com.alibaba.jvm.sandbox.repeater.plugin.core.impl.api;

import com.alibaba.jvm.sandbox.api.ProcessControlException;
import com.alibaba.jvm.sandbox.api.event.*;
import com.alibaba.jvm.sandbox.api.event.Event.Type;
import com.alibaba.jvm.sandbox.api.listener.EventListener;
import com.alibaba.jvm.sandbox.repeater.plugin.api.InvocationListener;
import com.alibaba.jvm.sandbox.repeater.plugin.api.InvocationProcessor;
import com.alibaba.jvm.sandbox.repeater.plugin.core.bridge.ClassloaderBridge;
import com.alibaba.jvm.sandbox.repeater.plugin.core.cache.RecordCache;
import com.alibaba.jvm.sandbox.repeater.plugin.core.cache.RepeatCache;
import com.alibaba.jvm.sandbox.repeater.plugin.core.model.ApplicationModel;
import com.alibaba.jvm.sandbox.repeater.plugin.core.serialize.SerializeException;
import com.alibaba.jvm.sandbox.repeater.plugin.core.trace.SequenceGenerator;
import com.alibaba.jvm.sandbox.repeater.plugin.core.trace.TraceContext;
import com.alibaba.jvm.sandbox.repeater.plugin.core.trace.Tracer;
import com.alibaba.jvm.sandbox.repeater.plugin.core.wrapper.SerializerWrapper;
import com.alibaba.jvm.sandbox.repeater.plugin.domain.Invocation;
import com.alibaba.jvm.sandbox.repeater.plugin.domain.InvokeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * <b></b>在整个录制过程中，repeater进行录制的处理流程是DefaultEventListener类中实现。</b>
 *
 * {@link DefaultEventListener} 默认的事件监听器实现类
 * <p>
 * 事件监听实现主要对接sandbox分发过来的事件
 * <p>
 * 基于普通单个方法的录制（一个方法的around事件before/return/throw录制入参和返回值)该实现类可以直接完成需求
 * <p>
 * 对于多个入口方法组合（例如：onRequest获取入参onResponse获取返回值）这种情况，需要重写 doBefore/doRequest/doThrow 自己控制流程
 * </p>
 *
 * 在DefaultEventListener中，承接的是所有事件的处理，也就是说无论是录制操作，还是回放操作，
 * 都是集中在这个类中实现的，只是根据不同的条件来区分是录制流量还是回放流量，从而判断该执行录制还是该执行回放。
 * @author zhaoyb1990
 */
public class DefaultEventListener implements EventListener {

    protected static Logger log = LoggerFactory.getLogger(DefaultEventListener.class);

    protected final InvokeType invokeType;
    protected final boolean entrance;
    protected final InvocationListener listener;
    protected final InvocationProcessor processor;
    /**
     * 事件偏移量计算；由于java方法本身存在多层嵌套调用，在一次invoke中，我们只录制最外层拦截到的方法，往下递归的树不做拦截
     * <p>
     * 通过事件偏移量进行判断
     */
    private ThreadLocal<AtomicInteger> eventOffset = new ThreadLocal<AtomicInteger>() {
        @Override
        protected AtomicInteger initialValue() {
            return new AtomicInteger(0);
        }
    };

    public DefaultEventListener(InvokeType invokeType, boolean entrance,
                                InvocationListener listener,
                                InvocationProcessor processor) {
        this.invokeType = invokeType;
        this.entrance = entrance;
        this.listener = listener;
        this.processor = processor;
    }

    @Override
    public void onEvent(Event event) throws Throwable {
        try {
            /*
             * event过滤；针对单个listener，只处理top的事件
             */
            if (!isTopEvent(event)) {
                if (log.isDebugEnabled()) {
                    log.debug("not top event ,type={},event={},offset={}", invokeType, event, eventOffset.get().get());
                }
                return;
            }
            /*
             * 初始化Tracer
             */
            initContext(event);
            /*
             * 执行基础过滤
             */
            if (!access(event)) {
                if (log.isDebugEnabled()) {
                    log.debug("event access failed,type={},event={}", invokeType, event);
                }
                return;
            }
            /*
             * 执行采样计算（只有entrance插件负责计算采样，子调用插件不计算)
             */
            if (!sample(event)) {
                if (log.isDebugEnabled()) {
                    log.debug("event missing sample rule,type={},event={}", invokeType, event);
                }
                return;
            }
            /*
             * processor filter
             * 判断是否是插件调用处理器设置为忽略的事件。每个插件处理不同。
             */
            if (processor != null && processor.ignoreEvent((InvokeEvent) event)) {
                if (log.isDebugEnabled()) {
                    log.debug("event is ignore by processor,type={},event={},processor={}", invokeType, event, processor);
                }
                return;
            }
            /*
             * 分发事件处理（对于一次around事件可以收集到入参/返回值的可以直接使用；需要从多次before实践获取的）
             */
            switch (event.type) {
                case BEFORE:
                    doBefore((BeforeEvent) event);
                    break;
                case RETURN:
                    doReturn((ReturnEvent) event);
                    break;
                case THROWS:
                    doThrow((ThrowsEvent) event);
                    break;
                default:
                    break;
            }
        } catch (ProcessControlException pe) {
            /*
             * sandbox流程干预
             */
            // process control 会中断事件，不会有return/throw事件过来，因此需要清除偏移量
            eventOffset.remove();
            throw pe;
        } catch (Throwable throwable) {
            // uncaught exception
            log.error("[Error-0000]-uncaught exception occurred when dispatch event,type={},event={}", invokeType, event, throwable);
            ApplicationModel.instance().exceptionOverflow(throwable);
        } finally {
            /*
             * 入口插件 && 完成事件
             */
             clearContext(event);
         }
    }

    /**
     * 处理before事件
     *
     * 1. 判断当前流量是否为回放流量，若为回放流量则调用processor.doMock方法执行Mock，不执行后续操作。
     * 2. 若当前流量非回放流量，则基于当前获取到的信息拼接Invocation实例。
     *    其中会调用插件调用处理器的assembleRequest、assembleResponse、assembleThrowable、assembleIdentity方法进行请求参数、
     *    返回结果、抛出异常、调用标识的拼接。
     * 3. 根据插件调用处理器的设定，判断是否需要进行Invocation中的request、response、throwable参数的序列化。
     * 4. 将当前事件的Invocation信息存放到录制缓存中。
     * @param event before事件
     */
    protected void doBefore(BeforeEvent event) throws ProcessControlException {
        // 回放流量；如果是入口则放弃；子调用则进行mock
        if (RepeatCache.isRepeatFlow(Tracer.getTraceId())) {
            processor.doMock(event, entrance, invokeType);
            return;
        }
        Invocation invocation = initInvocation(event);
        invocation.setStart(System.currentTimeMillis());
        invocation.setTraceId(Tracer.getTraceId());
        invocation.setIndex(entrance ? 0 : SequenceGenerator.generate(Tracer.getTraceId()));
        invocation.setIdentity(processor.assembleIdentity(event));
        invocation.setEntrance(entrance);
        invocation.setType(invokeType);
        invocation.setProcessId(event.processId);
        invocation.setInvokeId(event.invokeId);
        invocation.setRequest(processor.assembleRequest(event));
        invocation.setResponse(processor.assembleResponse(event));
        invocation.setSerializeToken(ClassloaderBridge.instance().encode(event.javaClassLoader));
        try {
            // fix issue#14 : useGeneratedKeys
            if (processor.inTimeSerializeRequest(invocation, event)) {
                SerializerWrapper.inTimeSerialize(invocation);
            }
        } catch (SerializeException e) {
            Tracer.getContext().setSampled(false);
            log.error("Error occurred serialize", e);
        }
        RecordCache.cacheInvocation(event.invokeId, invocation);
    }

    /**
     * 初始化invocation
     * 放开给插件重写，可以初始化自定义的调用描述类型，模块不感知插件的类型
     *
     * @param beforeEvent before事件
     * @return 一次调用
     */
    protected Invocation initInvocation(BeforeEvent beforeEvent) {
        return new Invocation();
    }

    /**
     * 计算采样率
     *
     * 执行采样计算（只有entrance插件负责计算采样，子调用插件不计算)，该过滤条件与repeaterConfig中的sample字段有关。
     * @param event 事件
     * @return 是否采样
     */
    protected boolean sample(Event event) {
        if (entrance && event.type == Type.BEFORE) {
            return Tracer.getContext().inTimeSample(invokeType);
        } else {
            final TraceContext context = Tracer.getContext();
            return context != null && context.isSampled();
        }
    }

    /**
     * return事件与throw事件的处理逻辑基本一致。
     *
     * 判断当前流量是否为回放流量，若为回放流量则不执行后续操作。
     *
     * 从录制缓存中获取对应的Invocation实例，如果获取失败则打印失败日志，不执行后续操作
     *
     * Invocation实例获取成功后，调用插件调用处理器的assembleResponse或者assembleThrowable方法将reponse或者throwable信息设置到Invocation实例中。并设定Invocation的结束时间。
     *
     * 回调调用监听器InvocationListener的onInvocation方法，判断Invocation是否是一个入口调用，如果是则调用消息投递器的broadcastRecord将录制记录序列化后上传给repeater-console。如果不是则当做子调用保存到录制缓存中。
     *
     * *PS：消息投递器当前支持两种模式，在standalone模式下，保存到本地文件；在非standalone模式下上传到repeater-console。
     * 处理return事件
     *
     * @param event return事件
     */
    protected void doReturn(ReturnEvent event) {
        if (RepeatCache.isRepeatFlow(Tracer.getTraceId())) {
            return;
        }
        Invocation invocation = RecordCache.getInvocation(event.invokeId);
        if (invocation == null) {
            log.debug("no valid invocation found in return,type={},traceId={}", invokeType, Tracer.getTraceId());
            return;
        }
        invocation.setResponse(processor.assembleResponse(event));
        invocation.setEnd(System.currentTimeMillis());
        listener.onInvocation(invocation);
    }

    /**
     * 处理throw事件
     *
     * @param event throw事件
     */
    protected void doThrow(ThrowsEvent event) {
        if (RepeatCache.isRepeatFlow(Tracer.getTraceId())) {
            return;
        }
        Invocation invocation = RecordCache.getInvocation(event.invokeId);
        if (invocation == null) {
            log.debug("no valid invocation found in throw,type={},traceId={}", invokeType, Tracer.getTraceId());
            return;
        }
        invocation.setThrowable(processor.assembleThrowable(event));
        invocation.setEnd(System.currentTimeMillis());
        listener.onInvocation(invocation);
    }

    /**
     * 关注的事件
     *
     * 针对单个listener，只处理top的事件
     * 不同的插件之间的listener相互隔离，所以即使是录制http的请求，java子调用插件的事件也能通过过滤。
     * @param event 事件
     * @return 是否关注
     */
    protected boolean isTopEvent(Event event) {
        boolean isTop;
        switch (event.type) {
            case THROWS:
            case RETURN:
                isTop = eventOffset.get().decrementAndGet() == 0;
                break;
            case BEFORE:
                isTop = eventOffset.get().getAndIncrement() == 0;
                break;
            default:
                isTop = false;
                break;
        }
        // eventOffset == 0 代表是该线程的最后一次return/throw事件，需要主动清理资源
        if (eventOffset.get().get() == 0) {
            eventOffset.remove();
        }
        return isTop;
    }

    /**
     * 初始化上下文；
     * 只有entrance插件负责初始化和清理上下文
     * 子调用无需关心traceContext信息（多线程情况下由ttl负责copy和restore，单线程由entrance负责管理）
     *
     * 当事件通过第一个过滤时，就会进行跟踪器初始化。
     * 跟踪器相关的内容在com.alibaba.jvm.sandbox.repeater.plugin.core.trace包中。
     * 其中我们提到的traceId实际上是这个跟踪器的独立标识。所以无论是录制和回放都会有其独立的traceId。
     * @param event 事件
     */
    protected void initContext(Event event) {
        if (entrance && isEntranceBegin(event)) {
            Tracer.start();
        }
    }

    /**
     * 是否入口处理开始
     *
     * @param event 事件
     * @return true/false
     */
    protected boolean isEntranceBegin(Event event) {
        return event.type == Type.BEFORE;
    }

    /**
     * 清理上下文
     *
     * @param event 事件
     */
    private void clearContext(Event event) {
        if (entrance && isEntranceFinish(event)) {
            Tracer.end();
        }
    }

    /**
     * 是否入口处理完成;非around事件需要重写
     *
     * @param event 事件
     * @return true/false
     */
    protected boolean isEntranceFinish(Event event) {
        return event.type != Type.BEFORE
                // 开启trace的类型负责清理
                && Tracer.getContext().getInvokeType() == invokeType;
    }

    /**
     * 事件是否可以通过
     * <p>
     * 降级之后只有回放流量可以通过
     *
     * 进行基础过滤，主要根据系统熔断、降级进行判断。该过滤条件与repeaterConfig中的degrade字段、exceptionThreshold字段有关。
     * @param event 事件
     * @return 是否通过
     */
    protected boolean access(Event event) {
        return ApplicationModel.instance().isWorkingOn() &&
                (!ApplicationModel.instance().isDegrade() || RepeatCache.isRepeatFlow(Tracer.getTraceId()));
    }

    @Override
    public String toString() {
        return "DefaultEventListener:invokeType=" + invokeType + ";entrance=" + entrance;
    }
}
