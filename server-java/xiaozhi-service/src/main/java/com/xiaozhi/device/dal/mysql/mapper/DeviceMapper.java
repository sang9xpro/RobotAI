package com.xiaozhi.device.dal.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaozhi.device.dal.mysql.dataobject.DeviceDO;
import com.xiaozhi.device.model.DeviceProjection;
import com.xiaozhi.device.model.AdminRobotProjection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DeviceMapper extends BaseMapper<DeviceDO> {
    IPage<AdminRobotProjection> selectHistoryDevices(Page<AdminRobotProjection> page,
            @Param("filter") com.xiaozhi.common.model.req.HistoryDevicePageReq filter,
            @Param("scopeUserId") Integer scopeUserId);
    IPage<AdminRobotProjection> selectAdminRobots(Page<AdminRobotProjection> page, @Param("search") String search);

    IPage<DeviceProjection> selectPage(Page<DeviceProjection> page,
                                       @Param("deviceId") String deviceId,
                                       @Param("deviceName") String deviceName,
                                       @Param("roleName") String roleName,
                                       @Param("state") String state,
                                       @Param("roleId") Integer roleId,
                                       @Param("userId") Integer userId);
}
