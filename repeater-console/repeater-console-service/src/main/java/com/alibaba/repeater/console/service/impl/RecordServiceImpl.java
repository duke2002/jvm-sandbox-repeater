package com.alibaba.repeater.console.service.impl;

import com.alibaba.jvm.sandbox.repeater.aide.compare.Comparable;
import com.alibaba.jvm.sandbox.repeater.aide.compare.ComparableFactory;
import com.alibaba.jvm.sandbox.repeater.aide.compare.CompareResult;
import com.alibaba.jvm.sandbox.repeater.plugin.core.serialize.SerializeException;
import com.alibaba.jvm.sandbox.repeater.plugin.core.wrapper.RecordWrapper;
import com.alibaba.jvm.sandbox.repeater.plugin.core.wrapper.SerializerWrapper;
import com.alibaba.jvm.sandbox.repeater.plugin.domain.RepeatModel;
import com.alibaba.jvm.sandbox.repeater.plugin.domain.RepeaterResult;
import com.alibaba.repeater.console.common.domain.PageResult;
import com.alibaba.repeater.console.common.domain.RecordBO;
import com.alibaba.repeater.console.common.domain.RecordDetailBO;
import com.alibaba.repeater.console.common.domain.ReplayStatus;
import com.alibaba.repeater.console.common.params.RecordParams;
import com.alibaba.repeater.console.dal.dao.RecordDao;
import com.alibaba.repeater.console.dal.model.Record;
import com.alibaba.repeater.console.dal.model.Replay;
import com.alibaba.repeater.console.service.RecordService;
import com.alibaba.repeater.console.service.convert.ModelConverter;
import com.alibaba.repeater.console.service.util.ConvertUtil;
import com.alibaba.repeater.console.service.util.JacksonUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.stream.Collectors;

/**
 * {@link RecordServiceImpl} 使用mysql实现存储
 * <p>
 *
 * @author zhaoyb1990
 */
@Service("recordService")
@Slf4j
public class RecordServiceImpl implements RecordService {

    @Resource
    private RecordDao recordDao;
    @Resource
    private ModelConverter<Record, RecordBO> recordConverter;
    @Resource
    private ModelConverter<Record, RecordDetailBO> recordDetailConverter;

    /**
     * 添加录制的记录
     * @param body post内存
     * @return
     */
    @Override
    public RepeaterResult<String> saveRecord(String body) {
        try {
            // 把输入值反序列化成RecordWrapper对象
            RecordWrapper wrapper = SerializerWrapper.hessianDeserialize(body, RecordWrapper.class);
            // 如果反序列化失败，直接返回错误
            if (wrapper == null || StringUtils.isEmpty(wrapper.getAppName())) {
                return RepeaterResult.builder().success(false).message("invalid request").build();
            }
            // 把wrapper+原始传入的body，组合成record。主要是添加了一个创建日期，大部分 wrapper 和 record 一一对应地存储，
            // 以及把整个 body 放到 wrapperRecord 对象中作为存档
            Record record = ConvertUtil.convertWrapper(wrapper, body);
            recordDao.insert(record);
            // 保存成功，就可以返回了。
            return RepeaterResult.builder().success(true).message("operate success").data("-/-").build();
        } catch (Throwable throwable) {
            return RepeaterResult.builder().success(false).message(throwable.getMessage()).build();
        }
    }

    /**
     * 根据应用名和traceId， 获取序列化后的录制数据
     * @param appName 应用名
     * @param traceId traceId
     * @return
     */
    @Override
    public RepeaterResult<String> get(String appName, String traceId) {
        Record record = recordDao.selectByAppNameAndTraceId(appName, traceId);
        // 在此处打断点，然后调试中执行SerializerWrapper.hessianDeserialize(record.getWrapperRecord(),
        // RecordModel.class)即可查看详情
        if (record == null) {
            return RepeaterResult.builder().success(false).message("data not exits").build();
        }
        return RepeaterResult.builder().success(true).message("operate success").data(record.getWrapperRecord()).build();
    }

    @Override
    public PageResult<RecordBO> query(RecordParams params) {
        Page<Record> page = recordDao.selectByAppNameOrTraceId(params);
        PageResult<RecordBO> result = new PageResult<>();
        if (page.hasContent()) {
            result.setSuccess(true);
            result.setCount(page.getTotalElements());
            result.setTotalPage(page.getTotalPages());
            result.setPageIndex(params.getPage());
            result.setPageSize(params.getSize());
            result.setData(page.getContent().stream().map(recordConverter::convert).collect(Collectors.toList()));
        }
        return result;
    }

    @Override
    public RepeaterResult<RecordDetailBO> getDetail(RecordParams params) {
        Record record = recordDao.selectByAppNameAndTraceId(params.getAppName(), params.getTraceId());
        if (record == null) {
            return RepeaterResult.builder().message("data not found").build();
        }
        return RepeaterResult.builder().success(true).data(recordDetailConverter.convert(record)).build();
    }

    /**
     * 根据repeatId获取回放执行结果
     * @param repeatId 回放ID
     * @return
     */
    @Override
    public RepeaterResult<RepeatModel> callback(String repeatId) {
        return null;
    }
}
