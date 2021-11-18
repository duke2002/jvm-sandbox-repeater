package com.alibaba.jvm.sandbox.repeater.module;

import com.alibaba.jvm.sandbox.api.Information;
import com.alibaba.jvm.sandbox.api.Information.Mode;
import com.alibaba.jvm.sandbox.api.Module;
import com.alibaba.jvm.sandbox.api.ModuleException;
import com.alibaba.jvm.sandbox.api.ModuleLifecycle;
import com.alibaba.jvm.sandbox.api.annotation.Command;
import com.alibaba.jvm.sandbox.api.resource.*;
import com.alibaba.jvm.sandbox.repeater.module.advice.SpringInstantiateAdvice;
import com.alibaba.jvm.sandbox.repeater.module.classloader.PluginClassLoader;
import com.alibaba.jvm.sandbox.repeater.module.classloader.PluginClassRouting;
import com.alibaba.jvm.sandbox.repeater.module.impl.JarFileLifeCycleManager;
import com.alibaba.jvm.sandbox.repeater.module.util.LogbackUtils;
import com.alibaba.jvm.sandbox.repeater.plugin.Constants;
import com.alibaba.jvm.sandbox.repeater.plugin.api.Broadcaster;
import com.alibaba.jvm.sandbox.repeater.plugin.api.ConfigManager;
import com.alibaba.jvm.sandbox.repeater.plugin.api.InvocationListener;
import com.alibaba.jvm.sandbox.repeater.plugin.api.LifecycleManager;
import com.alibaba.jvm.sandbox.repeater.plugin.core.StandaloneSwitch;
import com.alibaba.jvm.sandbox.repeater.plugin.core.bridge.ClassloaderBridge;
import com.alibaba.jvm.sandbox.repeater.plugin.core.bridge.RepeaterBridge;
import com.alibaba.jvm.sandbox.repeater.plugin.core.eventbus.EventBusInner;
import com.alibaba.jvm.sandbox.repeater.plugin.core.eventbus.RepeatEvent;
import com.alibaba.jvm.sandbox.repeater.plugin.core.impl.api.DefaultInvocationListener;
import com.alibaba.jvm.sandbox.repeater.plugin.core.model.ApplicationModel;
import com.alibaba.jvm.sandbox.repeater.plugin.core.serialize.SerializeException;
import com.alibaba.jvm.sandbox.repeater.plugin.core.serialize.Serializer;
import com.alibaba.jvm.sandbox.repeater.plugin.core.serialize.SerializerProvider;
import com.alibaba.jvm.sandbox.repeater.plugin.core.spring.SpringContextInnerContainer;
import com.alibaba.jvm.sandbox.repeater.plugin.core.trace.TtlConcurrentAdvice;
import com.alibaba.jvm.sandbox.repeater.plugin.core.util.ExecutorInner;
import com.alibaba.jvm.sandbox.repeater.plugin.core.util.PathUtils;
import com.alibaba.jvm.sandbox.repeater.plugin.core.util.PropertyUtil;
import com.alibaba.jvm.sandbox.repeater.plugin.core.wrapper.SerializerWrapper;
import com.alibaba.jvm.sandbox.repeater.plugin.domain.RepeatMeta;
import com.alibaba.jvm.sandbox.repeater.plugin.domain.RepeaterConfig;
import com.alibaba.jvm.sandbox.repeater.plugin.domain.RepeaterResult;
import com.alibaba.jvm.sandbox.repeater.plugin.exception.PluginLifeCycleException;
import com.alibaba.jvm.sandbox.repeater.plugin.spi.InvokePlugin;
import com.alibaba.jvm.sandbox.repeater.plugin.spi.Repeater;
import com.alibaba.jvm.sandbox.repeater.plugin.spi.SubscribeSupporter;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.MetaInfServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.alibaba.jvm.sandbox.repeater.plugin.Constants.REPEAT_SPRING_ADVICE_SWITCH;

/**
 * 模块类，实现沙箱模块生命周期各个阶段逻辑实现
 *
 * RepeaterModule实现了ModuleLifecycle接口，在sandbox进行模块加载时，对于实现该接口的实例会一次执行其onLoad方法、onActive方法、onCompleted方法，
 *
 * repeater模块的初始化，插件挂载，插件初始化流程。提供回放接口，更新配置接口入口
 * <p>
 *
 * @author zhaoyb1990
 */
@MetaInfServices(Module.class)
@Information(id = com.alibaba.jvm.sandbox.repeater.module.Constants.MODULE_ID, author = "zhaoyb1990", version = com.alibaba.jvm.sandbox.repeater.module.Constants.VERSION)
public class RepeaterModule implements Module, ModuleLifecycle {

    private final static Logger log = LoggerFactory.getLogger(RepeaterModule.class);

    /**
     * 事件观察者
     */
    @Resource
    private ModuleEventWatcher eventWatcher;

    /**
     * 模块控制接口,控制模块的激活和冻结
     */
    @Resource
    private ModuleController moduleController;

    /**
     * sandbox启动配置，这里只用来判断启动模式
     */
    @Resource
    private ConfigInfo configInfo;

    @Resource
    private ModuleManager moduleManager;

    /**
     * 已加载类数据源,可以获取到所有已加载类的集合
     */
    @Resource
    private LoadedClassDataSource loadedClassDataSource;

    /**
     * 消息广播服务；用于采集流量之后的消息分发（保存录制记录，保存回放结果、拉取录制记录）
     */
    private Broadcaster broadcaster;

    /**
     * 调用监听器。把broadcaster包装成监听器
     */
    private InvocationListener invocationListener;

    /**
     * 配置管理器，实现拉取配置
     */
    private ConfigManager configManager;

    /**
     * 插件加载器
     */
    private LifecycleManager lifecycleManager;

    /**
     * 插件列表
     */
    private List<InvokePlugin> invokePlugins;

    /**
     * 服务炭火
     */
    private HeartbeatHandler heartbeatHandler;

    /**
     * 插件列表
     */
    private AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * 如果是agent方式启动sandbox，则记录spring加载的bean
     * 模块加载，模块开始加载之前
     * @throws Throwable
     */
    @Override
    public void onLoad() throws Throwable {
        // 初始化日志框架
        LogbackUtils.init(PathUtils.getConfigPath() + "/repeater-logback.xml");
        // 获取启动模式
        Mode mode = configInfo.getMode();
        log.info("module on loaded,id={},version={},mode={}", com.alibaba.jvm.sandbox.repeater.module.Constants.MODULE_ID, com.alibaba.jvm.sandbox.repeater.module.Constants.VERSION, mode);
        /* agent方式启动 */
        if (mode == Mode.AGENT && Boolean.valueOf(PropertyUtil.getPropertyOrDefault(REPEAT_SPRING_ADVICE_SWITCH, ""))) {
            log.info("agent launch mode,use Spring Instantiate Advice to register bean.");
            // SpringContext内部容器agent模式设置为真
            SpringContextInnerContainer.setAgentLaunch(true);
            // spring初始化拦截器，agent启动模式下拦截记录beanName和bean
            SpringInstantiateAdvice.watcher(this.eventWatcher).watch();
            // 模块激活
            moduleController.active();
        }
    }

    /**
     * 模块卸载，模块开始卸载之前调用
     * @throws Throwable
     */
    @Override
    public void onUnload() throws Throwable {
        if (lifecycleManager != null) {
            // 释放插件加载资源，尽可能关闭pluginClassLoader
            lifecycleManager.release();
        }
        heartbeatHandler.stop();
    }

    /**
     * 模块激活
     * @throws Throwable
     */
    @Override
    public void onActive() throws Throwable {
        log.info("onActive");
    }

    /**
     * 模块冻结
     *
     * @throws Throwable
     */
    @Override
    public void onFrozen() throws Throwable {
        log.info("onFrozen");
    }

    /**
     * 模块加载完成，模块完成加载后调用！
     */
    @Override
    public void loadCompleted() {
        ExecutorInner.execute(new Runnable() {
            @Override
            public void run() {
                // 根据使用模式是单机版还是服务端板来获取拉取配置的实现方法
                configManager = StandaloneSwitch.instance().getConfigManager();
                // 根据使用模式是单机版还是服务端板来获取消息广播服务
                broadcaster = StandaloneSwitch.instance().getBroadcaster();
                // 调用监听器实例化
                invocationListener = new DefaultInvocationListener(broadcaster);
                // 拉取配置
                RepeaterResult<RepeaterConfig> pr = configManager.pullConfig();
                if (pr.isSuccess()) {
                    log.info("pull repeater config success,config={}", pr.getData());
                    // 根据已加载类数据源初始化类加载器连接桥
                    ClassloaderBridge.init(loadedClassDataSource);
                    // 根据配置进行插件初始化
                    initialize(pr.getData());
                }
            }
        });
        heartbeatHandler = new HeartbeatHandler(configInfo, moduleManager);
        heartbeatHandler.start();
    }

    /**
     * 初始化插件
     *
     * @param config 配置文件
     */
    private synchronized void initialize(RepeaterConfig config) {
        // 如果插件没有被初始化，才开始初始化
        if (initialized.compareAndSet(false, true)) {
            try {
                ApplicationModel.instance().setConfig(config);
                // 特殊路由表;
                PluginClassLoader.Routing[] routingArray = PluginClassRouting.wellKnownRouting(configInfo.getMode() == Mode.AGENT, 20L);
                String pluginsPath;
                if (StringUtils.isEmpty(config.getPluginsPath())) {
                    pluginsPath = PathUtils.getPluginPath();
                } else {
                    pluginsPath = config.getPluginsPath();
                }

                lifecycleManager = new JarFileLifeCycleManager(pluginsPath, routingArray);
                // 装载插件
                invokePlugins = lifecycleManager.loadInvokePlugins();
                for (InvokePlugin invokePlugin : invokePlugins) {
                    try {
                        if (invokePlugin.enable(config)) {
                            log.info("enable plugin {} success", invokePlugin.identity());
                            //这里最终就是调用了BuildingForBehavior.onWatch，如果看过上一篇文章就知道，sandbox就是在这个阶段实现类增强
                            invokePlugin.watch(eventWatcher, invocationListener);
                            invokePlugin.onConfigChange(config);
                        }
                    } catch (PluginLifeCycleException e) {
                        log.info("watch plugin occurred error", e);
                    }
                }
                // 装载回放器。
                // 通过spi的方式加载插件中定义的回放器，并且设置结果发送服务器（可以是本地存储，也可以发送的console）
                List<Repeater> repeaters = lifecycleManager.loadRepeaters();
                for (Repeater repeater : repeaters) {
                    if (repeater.enable(config)) {
                        repeater.setBroadcast(broadcaster);
                    }
                }
                RepeaterBridge.instance().build(repeaters);
                // 装载消息订阅器
                List<SubscribeSupporter> subscribes = lifecycleManager.loadSubscribes();
                for (SubscribeSupporter subscribe : subscribes) {
                    subscribe.register();
                }
                // 线程池增强
                TtlConcurrentAdvice.watcher(eventWatcher).watch(config);
            } catch (Throwable throwable) {
                initialized.compareAndSet(true, false);
                log.error("error occurred when initialize module", throwable);
            }
        }
    }

    /**
     * 回放http接口
     * /sandbox/default/module/http/repeater/repeat
     *
     * @param req    请求参数
     * @param writer printWriter
     */
    @Command("repeat")
    public void repeat(final Map<String, String> req, final PrintWriter writer) {
        try {
            // 判断是否有"_data"参数，如果没有则返回报错
            String data = req.get(Constants.DATA_TRANSPORT_IDENTIFY);
            if (StringUtils.isEmpty(data)) {
                writer.write("invalid request, cause parameter {" + Constants.DATA_TRANSPORT_IDENTIFY + "} is required");
                return;
            }
            // 将回放请求的参数保存到RepeatEvent对象，并将这个对象推送到回放事件总线
            RepeatEvent event = new RepeatEvent();
            Map<String, String> requestParams = new HashMap<String, String>(16);
            for (Map.Entry<String, String> entry : req.entrySet()) {
                requestParams.put(entry.getKey(), entry.getValue());
            }
            event.setRequestParams(requestParams);
            EventBusInner.post(event);
            writer.write("submit success");
        } catch (Throwable e) {
            writer.write(e.getMessage());
        }
    }

    /**
     * 重新加载插件
     *
     * @param req    请求参数
     * @param writer printWriter
     */
    @Command("reload")
    public void reload(final Map<String, String> req, final PrintWriter writer) {
        try {
            if (initialized.compareAndSet(true,false)) {
                reload();
                initialized.compareAndSet(false, true);
            }
        } catch (Throwable throwable) {
            writer.write(throwable.getMessage());
            initialized.compareAndSet(false, true);
        }
    }

    private synchronized void reload() throws ModuleException {
        moduleController.frozen();
        // unwatch all plugin
        RepeaterResult<RepeaterConfig> result = configManager.pullConfig();
        if (!result.isSuccess()) {
            log.error("reload plugin failed, cause pull config not success");
            return;
        }
        for (InvokePlugin invokePlugin : invokePlugins) {
            if (invokePlugin.enable(result.getData())) {
                invokePlugin.unWatch(eventWatcher, invocationListener);
            }
        }
        // release classloader
        lifecycleManager.release();
        // reWatch
        initialize(result.getData());
        moduleController.active();
    }

    /**
     * 回放http接口(暴露JSON回放）
     *
     * @param req    请求参数
     * @param writer printWriter
     */
    @Command("repeatWithJson")
    public void repeatWithJson(final Map<String, String> req, final PrintWriter writer) {
        try {
            String data = req.get(Constants.DATA_TRANSPORT_IDENTIFY);
            if (StringUtils.isEmpty(data)) {
                writer.write("invalid request, cause parameter {" + Constants.DATA_TRANSPORT_IDENTIFY + "} is required");
                return;
            }
            RepeatMeta meta = SerializerProvider.instance().provide(Serializer.Type.JSON).deserialize(data, RepeatMeta.class);
            req.put(Constants.DATA_TRANSPORT_IDENTIFY, SerializerProvider.instance().provide(Serializer.Type.HESSIAN).serialize2String(meta));
            repeat(req, writer);
        } catch (Throwable e) {
            writer.write(e.getMessage());
        }
    }

    /**
     * 配置推送接口
     * 接口路径/sandbox/default/module/http/repeater/pushConfig
     *
     * @param req    请求参数
     * @param writer printWriter
     */
    @Command("pushConfig")
    public void pushConfig(final Map<String, String> req, final PrintWriter writer) {
        // 判断是否有"_data"参数，如果没有则返回报错
        String data = req.get(Constants.DATA_TRANSPORT_IDENTIFY);
        if (StringUtils.isEmpty(data)) {
            writer.write("invalid request, cause parameter {" + Constants.DATA_TRANSPORT_IDENTIFY + "} is required");
            return;
        }
        try {
            // 将请求参数序列化之后获取RepeaterConfig，并且通知插件更新配置
            RepeaterConfig config = SerializerWrapper.hessianDeserialize(data, RepeaterConfig.class);
            ApplicationModel.instance().setConfig(config);
            noticeConfigChange(config);
            writer.write("config push success");
        } catch (SerializeException e) {
            writer.write("invalid request, cause deserialize config failed, reason = {" + e.getMessage() + "}");
        }
    }

    /**
     * 通知配置变更
     *
     * @param config 配置文件
     */
    private void noticeConfigChange(final RepeaterConfig config) {
        // 如果模块初始化已成功，逐个插件进行更新通知
        if (initialized.get()) {
            for (InvokePlugin invokePlugin : invokePlugins) {
                try {
                    if (invokePlugin.enable(config)) {
                        invokePlugin.onConfigChange(config);
                    }
                } catch (PluginLifeCycleException e) {
                    log.error("error occurred when notice config, plugin ={}", invokePlugin.getType().name(), e);
                }
            }
        }
    }
}
