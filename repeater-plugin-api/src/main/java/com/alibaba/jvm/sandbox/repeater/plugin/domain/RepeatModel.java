package com.alibaba.jvm.sandbox.repeater.plugin.domain;

import java.util.List;


/**
 * repeater提供的一个回放结果记录。包括repeatId,是否完成，实际返回值，原始返回值，diff记录，耗时，tranceId
 * {@link RepeatModel} 回放消息数据类型
 * <p>
 *
 * @author zhaoyb1990
 */
public class RepeatModel implements java.io.Serializable {

    /**
     * 回放结果的 repeatId
     */
    private String repeatId;

    /**
     * 是否已回放完成
     */
    private boolean finish;

    /**
     * 本次回放返回的结果
     */
    private Object response;

    /**
     * 录制记录中这个入口调用返回的结果
     */
    private Object originResponse;

    /**
     * 回放结果与录制记录中的结果差异（由于官方没有实现该功能，所以默认返回 null）
     */
    private Object diff;

    /**
     * 回放耗时
     */
    private Long cost;

    /**
     * 回放记录的 traceId
     */
    private String traceId;

    /**
     * 回放过程中被 mock 的步骤的执行结果
     */
    private List<MockInvocation> mockInvocations;

    public String getRepeatId() {
        return repeatId;
    }

    public void setRepeatId(String repeatId) {
        this.repeatId = repeatId;
    }

    public boolean isFinish() {
        return finish;
    }

    public void setFinish(boolean finish) {
        this.finish = finish;
    }

    public Object getResponse() {
        return response;
    }

    public void setResponse(Object response) {
        this.response = response;
    }

    public Object getOriginResponse() {
        return originResponse;
    }

    public void setOriginResponse(Object originResponse) {
        this.originResponse = originResponse;
    }

    public Object getDiff() {
        return diff;
    }

    public void setDiff(Object diff) {
        this.diff = diff;
    }

    public Long getCost() {
        return cost;
    }

    public void setCost(Long cost) {
        this.cost = cost;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public List<MockInvocation> getMockInvocations() {
        return mockInvocations;
    }

    public void setMockInvocations(List<MockInvocation> mockInvocations) {
        this.mockInvocations = mockInvocations;
    }
}
