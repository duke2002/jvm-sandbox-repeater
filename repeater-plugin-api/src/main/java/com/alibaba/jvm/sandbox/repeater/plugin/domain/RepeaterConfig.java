package com.alibaba.jvm.sandbox.repeater.plugin.domain;

import com.google.common.collect.Lists;

import java.util.List;

/**
 * {@link RepeaterConfig} 基础配置项
 * <p>
 * 基础配置从服务端推送到启动的agent或者由agent启动的时候主动去服务端拉取配置；
 * <p>
 * 配置主要包含一些模块的工作模式；插件启动鉴权；采样率等
 * </p>
 *
 * @author zhaoyb1990
 * @since 1.0.0
 */
public class RepeaterConfig implements java.io.Serializable{

    /**
     * 是否开启ttl线程上下文切换
     * <p>
     * 开启之后，才能将并发线程中发生的子调用记录下来，否则无法录制到并发子线程的子调用信息
     * <p>
     * 原理是将住线程的threadLocal拷贝到子线程，执行任务完成后恢复
     *
     * boolean，默认填 true 即可
     * @see com.alibaba.ttl.TransmittableThreadLocal
     */
    private boolean useTtl;

    /**
     * 是否执行录制降级策略
     * <p>
     * 开启之后，不进行录制，只处理回放请求
     * boolean，默认填 false 即可	当前只使用过 false
     * 按照字面理解就是当这个改为 true 之后，不再进行录制。
     * 涉及的关键方法：com.alibaba.jvm.sandbox.repeater.plugin.core.impl.api.DefaultEventListener#access
     */
    private boolean degrade;

    /**
     * 异常发生阈值；默认1000
     * 当{@code ExceptionAware} 感知到异常次数超过阈值后，会降级模块
     * 当出现降级则不再进行任何录制。
     * 涉及的关键方法：com.alibaba.jvm.sandbox.repeater.plugin.core.impl.api.DefaultEventListener#access
     */
    private Integer exceptionThreshold = 1000;

    /**
     * 采样率；最小力度万分之一
     * 10000 代表 100%
     * 可以结合这个方法理解com.alibaba.jvm.sandbox.repeater.plugin.core.trace.TraceContext#inTimeSample
     */
    private Integer sampleRate = 10000;

    /**
     * 插件地址
     * 插件路径	String，
     * 默认填 null 即可
     */
    private String pluginsPath;

    /**
     * 由于HTTP接口的量太大（前后端未分离的情况可能还有静态资源）因此必须走白名单匹配模式才录制
     *
     * 需要录制和回放的 http 接口
     * 需要同时在 pluginIdedentities 和 repeatIdentities 中都配置了http这个配置才生效
     *
     * 参数支持正则表达式："^/alertService/.*$"
     */
    private List<String> httpEntrancePatterns = Lists.newArrayList();

    /**
     * java入口插件动态增强的行为
     * 需要录制和回放的 java 方法的入口
     * 需要同时在 pluginIdedentities 配置了java-entrance以及 repeatIdentities 配置了java这个配置才生效
     * 类名、方法名、以及是否包含子方法（若为 true，则匹配该类下的所有子类或者实现类，实际是否可用，有待验证），支持正则表达式
     *
     * 如下配置的意思就是 com.test.utils 包下所有类和所有方法
     * {
     * "classPattern": "com.test.utils.*",
     * "methodPatterns": [ "*" ],
     * "includeSubClasses": false
     * }
     * 如果该入口方法在某个 http 入口的调用链路下，可能不会被录制到，如 com.test.controller.hello() 方法，
     * 本身对应着 “/hello 的访问路径，则录制时无法录制到以这个 hello 方法为入口的 java 录制记录”
     */
    private List<Behavior> javaEntranceBehaviors = Lists.newArrayList();

    /**
     * java子调用插件动态增强的行为
     * 需要录制和 mock 的 java 方法的配置
     * 需要 pluginIdedentities 配置了java-subInvoke这个配置才生效
     * 类名、方法名、以及是否包含子方法（若为 true，则匹配该类下的所有子类或者实现类，实际是否可用，有待验证），支持正则表达式
     *
     * 如下配置的意思就是 com.test.server.utils 包下所有类和所有方法
     * {
     * "classPattern": "com.test.server.utils.*",
     * "methodPatterns": [ "*" ],
     * "includeSubClasses": false
     * }
     */
    private List<Behavior> javaSubInvokeBehaviors = Lists.newArrayList();

    /**
     * 需要启动的插件
     * 录制所使用的插件列表，配置了相应的插件名称，才能启用对应类别插件类别的录制
     * 插件名称:
     * 有效值有："http", "java-entrance", "java-subInvoke", "mybatis", "redis","ibatis","dubbo-consumer","dubbo-provider"
     * 1、插件配置生效还需要~/.sandbox-module/plugins/有对应的插件 jar 包。
     * 2、该参数有效值字段对应的取值是源码中实现了InvokePlugin的类的identity方法。
     */
    private List<String> pluginIdentities = Lists.newArrayList();

    /**
     * 回放器插件
     * 回放所使用的插件列表，配置了对应的插件，才能进行对应类别的回放
     * 插件名称: 有效值有："http", java", "dubbo"
     * 1、插件配置生效还需要~/.sandbox-module/plugins/有对应的插件 jar 包。
     * 2、该参数有效值字段对应的取值是源码中实现了Repeater的类的identity方法。
     */
    private List<String> repeatIdentities = Lists.newArrayList();

    public boolean isUseTtl() {
        return useTtl;
    }

    public void setUseTtl(boolean useTtl) {
        this.useTtl = useTtl;
    }

    public boolean isDegrade() {
        return degrade;
    }

    public void setDegrade(boolean degrade) {
        this.degrade = degrade;
    }

    public Integer getExceptionThreshold() {
        return exceptionThreshold;
    }

    public void setExceptionThreshold(Integer exceptionThreshold) {
        this.exceptionThreshold = exceptionThreshold;
    }

    public Integer getSampleRate() {
        return sampleRate;
    }

    public void setSampleRate(Integer sampleRate) {
        this.sampleRate = sampleRate;
    }

    public String getPluginsPath() {
        return pluginsPath;
    }

    public void setPluginsPath(String pluginsPath) {
        this.pluginsPath = pluginsPath;
    }

    public List<String> getHttpEntrancePatterns() {
        return httpEntrancePatterns;
    }

    public void setHttpEntrancePatterns(List<String> httpEntrancePatterns) {
        this.httpEntrancePatterns = httpEntrancePatterns;
    }

    public List<Behavior> getJavaEntranceBehaviors() {
        return javaEntranceBehaviors;
    }

    public void setJavaEntranceBehaviors(List<Behavior> javaEntranceBehaviors) {
        this.javaEntranceBehaviors = javaEntranceBehaviors;
    }

    public List<Behavior> getJavaSubInvokeBehaviors() {
        return javaSubInvokeBehaviors;
    }

    public void setJavaSubInvokeBehaviors(List<Behavior> javaSubInvokeBehaviors) {
        this.javaSubInvokeBehaviors = javaSubInvokeBehaviors;
    }

    public List<String> getPluginIdentities() {
        return pluginIdentities;
    }

    public void setPluginIdentities(List<String> pluginIdentities) {
        this.pluginIdentities = pluginIdentities;
    }

    public List<String> getRepeatIdentities() {
        return repeatIdentities;
    }

    public void setRepeatIdentities(List<String> repeatIdentities) {
        this.repeatIdentities = repeatIdentities;
    }

    @Override
    public String toString() {
        return "{" +
                "sampleRate=" + sampleRate +
                ", plugin=" + pluginIdentities +
                '}';
    }
}
