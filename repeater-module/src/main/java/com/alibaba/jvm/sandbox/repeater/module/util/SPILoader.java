package com.alibaba.jvm.sandbox.repeater.module.util;

import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;

import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 通过ServiceLoader加载，看了调用方主要是JarFileLifeCycleManager，主要是用来加载repeater插件的的spi实现
 * {@link SPILoader} 加载spi
 * <p>
 * 通过这个方法可以通过@MetaInfServices注解将插件中SPI接口的实现类加载。
 *
 * @author zhaoyb1990
 */
public class SPILoader {

    private final static Logger log = LoggerFactory.getLogger(SPILoader.class);

    public static <T> List<T> loadSPI(Class<T> spiType, ClassLoader classLoader) {
        ServiceLoader<T> loaded = ServiceLoader.load(spiType, classLoader);
        Iterator<T> spiIterator = loaded.iterator();
        List<T> target = Lists.newArrayList();
        while (spiIterator.hasNext()) {
            try {
                target.add(spiIterator.next());
            } catch (Throwable e) {
                log.error("Error load spi {} >>> ", spiType.getCanonicalName(), e);
            }
        }
        return target;
    }
}
