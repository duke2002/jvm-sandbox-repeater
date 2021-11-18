package com.alibaba.jvm.sandbox.repeater.plugin.domain;


/**
 * Mock调用
 * <p>
 *
 * @author zhaoyb1990
 */
public class MockInvocation implements java.io.Serializable {

    /**
     * mock 步骤的序号
     */
    private int index;
    /**
     * 回放记录的 traceId
     */
    private String traceId;
    /**
     * 回放结果的 repeatId
     */
    private String repeatId;
    /**
     * 这个 mock 步骤是否执行成功
     */
    private boolean success;
    /**
     * 这个 mock 步骤是否被跳过
     */
    private boolean skip;
    /**
     * 这个 mock 步骤的耗时
     */
    private long cost;
    /**
     * 这个 mock 步骤的录制时的标识 url
     */
    private String originUri;
    /**
     * 这个 mock 步骤回放时标识 url
     */
    private String currentUri;
    /**
     * 这个 mock 步骤的录制时的入参
     */
    private Object[] originArgs;
    /**
     * 这个 mock 步骤回放时入参
     */
    private Object[] currentArgs;

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getRepeatId() {
        return repeatId;
    }

    public void setRepeatId(String repeatId) {
        this.repeatId = repeatId;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public boolean isSkip() {
        return skip;
    }

    public void setSkip(boolean skip) {
        this.skip = skip;
    }

    public long getCost() {
        return cost;
    }

    public void setCost(long cost) {
        this.cost = cost;
    }

    public String getOriginUri() {
        return originUri;
    }

    public void setOriginUri(String originUri) {
        this.originUri = originUri;
    }

    public String getCurrentUri() {
        return currentUri;
    }

    public void setCurrentUri(String currentUri) {
        this.currentUri = currentUri;
    }

    public Object[] getOriginArgs() {
        return originArgs;
    }

    public void setOriginArgs(Object[] originArgs) {
        this.originArgs = originArgs;
    }

    public Object[] getCurrentArgs() {
        return currentArgs;
    }

    public void setCurrentArgs(Object[] currentArgs) {
        this.currentArgs = currentArgs;
    }
}
