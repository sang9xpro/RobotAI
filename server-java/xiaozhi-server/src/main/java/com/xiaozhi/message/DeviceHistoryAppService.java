package com.xiaozhi.message;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaozhi.common.model.PageResult;
import com.xiaozhi.common.model.req.DeviceHistoryPageReq;
import com.xiaozhi.common.model.req.HistoryDevicePageReq;
import com.xiaozhi.common.model.resp.AdminRobotResp;
import com.xiaozhi.common.model.resp.MessageResp;
import com.xiaozhi.device.dal.mysql.mapper.DeviceMapper;
import com.xiaozhi.device.model.AdminRobotProjection;
import com.xiaozhi.message.convert.MessageConvert;
import com.xiaozhi.message.dal.mysql.mapper.MessageMapper;
import com.xiaozhi.message.model.MessageProjection;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class DeviceHistoryAppService {
    @Resource private MessageMapper messageMapper;
    @Resource private DeviceMapper deviceMapper;
    @Resource private MessageConvert messageConvert;

    public PageResult<MessageResp> messages(DeviceHistoryPageReq filter, Integer scopeUserId) {
        var result = messageMapper.selectDeviceHistory(new Page<MessageProjection>(filter.getPageNo(), filter.getPageSize()), filter, scopeUserId);
        return new PageResult<>(result.getRecords().stream().map(messageConvert::toResp).toList(),
                result.getTotal(), filter.getPageNo(), filter.getPageSize());
    }
    public PageResult<AdminRobotResp> devices(HistoryDevicePageReq filter, Integer scopeUserId) {
        var result = deviceMapper.selectHistoryDevices(new Page<AdminRobotProjection>(filter.getPageNo(), filter.getPageSize()), filter, scopeUserId);
        return new PageResult<>(result.getRecords().stream().map(d -> new AdminRobotResp(d.getDeviceId(), d.getDeviceName(),
                d.getState(), d.getType(), d.getUserId(), d.getUsername(), d.getOwnerName())).toList(),
                result.getTotal(), filter.getPageNo(), filter.getPageSize());
    }
}
