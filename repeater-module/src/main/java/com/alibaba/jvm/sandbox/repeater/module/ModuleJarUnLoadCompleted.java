package com.alibaba.jvm.sandbox.repeater.module;

import com.alibaba.jvm.sandbox.api.spi.ModuleJarUnLoadSpi;
import com.alibaba.jvm.sandbox.repeater.module.util.LogbackUtils;
import org.kohsuke.MetaInfServices;

/**
 * 实现sandbox的ModuleJarUnLoadSpi，模块Jar文件卸载完所有模块后，
 * 正式卸载Jar文件之前调用的操作（这里只做了日志工具销毁操作）
 *
 * {@link }
 * <p>
 *
 * @author zhaoyb1990
 */
@MetaInfServices(ModuleJarUnLoadSpi.class)
public class ModuleJarUnLoadCompleted implements ModuleJarUnLoadSpi {

    @Override
    public void onJarUnLoadCompleted() {
        try {
            LogbackUtils.destroy();
        } catch (Throwable e) {
            // ignore
        }
    }
}
