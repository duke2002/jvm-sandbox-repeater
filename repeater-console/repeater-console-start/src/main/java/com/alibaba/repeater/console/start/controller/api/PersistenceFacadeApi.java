package com.alibaba.repeater.console.start.controller.api;

import com.alibaba.jvm.sandbox.repeater.plugin.domain.RepeatModel;
import com.alibaba.jvm.sandbox.repeater.plugin.domain.RepeaterResult;
import com.alibaba.repeater.console.common.params.ReplayParams;
import com.alibaba.repeater.console.service.RecordService;
import com.alibaba.repeater.console.service.ReplayService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * {@link PersistenceFacadeApi} Demo工程；作为repeater录制回放的数据存储
 * <p>
 *
 * @author zhaoyb1990
 */
@RestController
@RequestMapping("/facade/api")
public class PersistenceFacadeApi {

    @Resource
    private RecordService recordService;
    @Resource
    private ReplayService replayService;

    /**
     * 获取录制记录。该接口主要是供 repeater 在执行回放结果的时候，获取需要回放的记录用的。
     *
     * @param appName  录制记录的 appName
     * @param traceId  录制记录的 traceId
     * @return 返回结果为序列化后的录制记录，并不可读。
     */
    @RequestMapping(value = "record/{appName}/{traceId}", method = RequestMethod.GET)
    public RepeaterResult<String> getWrapperRecord(@PathVariable("appName") String appName,
                                                   @PathVariable("traceId") String traceId) {
        return recordService.get(appName, traceId);
    }

    /**
     * 触发回放
     *
     * 读取repeat.repeat.url 所配置的 url，触发相应的 repeater 执行回放的接口，供用户调用。
     * 能够触发单个录制记录的回放，需要提供被回放记录的 traceId 以及 appName。
     * 任务下发成功，则返回回放记录的 repeatId，用以查询回放结果。
     * 如果任务下发失败则返回异常信息。
     *
     * @param appName 需要回放的记录的应用名
     * @param ip
     * @param traceId 需要回放的记录的 traceId
     * @param request
     * @return
     */
    @RequestMapping(value = "repeat/{appName}/{ip}/{traceId}", method = RequestMethod.GET)
    public RepeaterResult<String> repeat(@PathVariable("appName") String appName,
                                         @PathVariable("ip") String ip,
                                         @PathVariable("traceId") String traceId,
                                         HttpServletRequest request) {
        // fix issue #63
        ReplayParams params = ReplayParams.builder()
                .repeatId(request.getHeader("RepeatId"))
                .ip(ip)
                .build();
        params.setAppName(appName);
        params.setTraceId(traceId);
        return replayService.replay(params);
    }

    /**
     * 该接口为供 repeater 保存录制记录调用，
     *
     * @param body 传入 RecordWrapper 序列化后的字符串
     * @return
     */
    @RequestMapping(value = "record/save", method = RequestMethod.POST)
    public RepeaterResult<String> recordSave(@RequestBody String body) {
        return recordService.saveRecord(body);
    }

    /**
     * 该接口为供 repeater 保存回放结果调用
     *
     * @param body 传入 RepeatModel 序列化后的字符串
     * @return
     */
    @RequestMapping(value = "repeat/save", method = RequestMethod.POST)
    public RepeaterResult<String> repeatSave(@RequestBody String body) {
        return replayService.saveRepeat(body);
    }

    /**
     * 获取单个记录的回放结果，详情见返回结果说明
     *
     * @param repeatId 回放结果的 repeatId
     * @return
     */
    @RequestMapping(value = "repeat/callback/{repeatId}", method = RequestMethod.GET)
    public RepeaterResult<RepeatModel> callback(@PathVariable("repeatId") String repeatId) {
        return recordService.callback(repeatId);
    }

}
