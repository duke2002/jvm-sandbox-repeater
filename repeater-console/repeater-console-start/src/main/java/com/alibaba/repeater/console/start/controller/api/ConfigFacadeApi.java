package com.alibaba.repeater.console.start.controller.api;

import java.util.Optional;

import com.alibaba.jvm.sandbox.repeater.plugin.domain.RepeaterConfig;
import com.alibaba.jvm.sandbox.repeater.plugin.domain.RepeaterResult;
import com.alibaba.repeater.console.common.domain.ModuleConfigBO;
import com.alibaba.repeater.console.common.params.ModuleConfigParams;
import com.alibaba.repeater.console.service.ModuleConfigService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * {@link ConfigFacadeApi} Demo工程；作为repeater录制回放的配置管理服务
 * <p>
 *
 * @author zhaoyb1990
 */
@RestController
@RequestMapping("/facade/api")
public class ConfigFacadeApi {

    @Resource
    private ModuleConfigService moduleConfigService;

    /**
     * 获取录制回放配置
     *
     * 供 repeater 调用的接口，repeater 以非 standalone 模式启动时会调用该接口获取录制回放配置，获取失败可能导致 repeater 启动失败。
     * 虽然接口有传入参数，但是由于接口没有实现根据 appName 以及 env 来区分不同的配置，传任何参数都会指向一个配置
     * @param appName 应用名称
     * @param env 环境名称
     * @return
     */
    @RequestMapping("/config/{appName}/{env}")
    public RepeaterResult<RepeaterConfig> getConfig(@PathVariable("appName") String appName,
                                                    @PathVariable("env") String env) {
        ModuleConfigParams params = new ModuleConfigParams();
        params.setAppName(appName);
        params.setEnvironment(env);
        RepeaterResult<ModuleConfigBO> result = moduleConfigService.query(params);
        // fix issue #83 npe
        return RepeaterResult.builder()
                .success(result.isSuccess())
                .message(result.getMessage())
                .data(result.getData() == null ? null : result.getData().getConfigModel())
                .build();
    }

}
