package com.xhzb.nursing.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xhzb.common.constant.CacheConstants;
import com.xhzb.common.utils.StringUtils;
import com.xhzb.nursing.domain.CheckIn;
import com.xhzb.nursing.domain.Device;
import com.xhzb.nursing.domain.DeviceData;
import com.xhzb.nursing.domain.Room;
import com.xhzb.nursing.mapper.CheckInMapper;
import com.xhzb.nursing.mapper.DeviceDataMapper;
import com.xhzb.nursing.mapper.DeviceMapper;
import com.xhzb.nursing.mapper.RoomMapper;
import com.xhzb.nursing.service.IRoomService;
import com.xhzb.nursing.domain.vo.BedVo;
import com.xhzb.nursing.domain.vo.DeviceVo;
import com.xhzb.nursing.domain.vo.RoomVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 房间Service业务层处理
 *
 * @author ruoyi
 * @date 2025-03-28
 */
@Service
public class RoomServiceImpl extends ServiceImpl<RoomMapper, Room> implements IRoomService {

    private static final Logger log = LoggerFactory.getLogger(RoomServiceImpl.class);

    @Autowired
    private RoomMapper roomMapper;

    @Autowired
    private DeviceMapper deviceMapper;

    @Autowired
    private DeviceDataMapper deviceDataMapper;

    @Autowired
    private CheckInMapper checkInMapper;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    /**
     * 查询房间
     *
     * @param id 房间主键
     * @return 房间
     */
    @Override
    public Room selectRoomById(Long id) {
        return getById(id);
    }

    /**
     * 查询房间列表
     *
     * @param room 房间
     * @return 房间
     */
    @Override
    public List<Room> selectRoomList(Room room) {
        return roomMapper.selectRoomList(room);
    }

    /**
     * 新增房间
     *
     * @param room 房间
     * @return 结果
     */
    @Override
    public int insertRoom(Room room) {
        return save(room) ? 1 : 0;
    }

    /**
     * 修改房间
     *
     * @param room 房间
     * @return 结果
     */
    @Override
    public int updateRoom(Room room) {
        return updateById(room) ? 1 : 0;
    }

    /**
     * 批量删除房间
     *
     * @param ids 需要删除的房间主键
     * @return 结果
     */
    @Override
    public int deleteRoomByIds(Long[] ids) {
        return removeByIds(Arrays.asList(ids)) ? 1 : 0;
    }

    /**
     * 根据楼层 id 获取房间视图对象列表
     *
     * @param floorId
     * @return
     */
    @Override
    public List<RoomVo> getRoomsByFloorId(Long floorId) {
        return roomMapper.selectByFloorId(floorId);
    }


    /**
     * 获取所有房间（负责老人）
     *
     * @param floorId
     * @return
     */
    @Override
    public List<RoomVo> getRoomsWithNurByFloorId(Long floorId) {
        return roomMapper.selectByFloorIdWithNur(floorId);
    }

    /**
     * 获取单个房间的视图对象（含楼层名、房型价格、房间编号）
     * <p>
     * 复用 selectByFloorId 的联表查询（room + floor + room_type + bed），
     * 取出该房间所在楼层后按 id 过滤，避免为单条查询再写一份 SQL。
     *
     * @param id 房间ID
     * @return 房间视图对象，不存在时返回 null
     */
    @Override
    public RoomVo selectRoomVoById(Long id) {
        if (id == null) {
            return null;
        }
        Room room = getById(id);
        if (room == null || room.getFloorId() == null) {
            return null;
        }
        List<RoomVo> roomVos = roomMapper.selectByFloorId(room.getFloorId());
        if (roomVos == null) {
            return null;
        }
        return roomVos.stream()
                .filter(vo -> id.equals(vo.getId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取楼层下所有房间及其绑定设备（智能床位）
     * <p>
     * 房间挂绑定该房间的固定设备；床位挂绑定该床位的固定设备 + 该床位老人身上的随身设备；
     * 设备上带各功能点最新一条上报数据（device_data 按 iot_id + function_id 取最新）。
     *
     * @param floorId 楼层ID，为空时返回空集合（前端楼层未选中时会请求空 id，避免 404）
     * @return 房间视图对象集合
     */
    @Override
    public List<RoomVo> getRoomsWithDeviceByFloorId(Long floorId) {
        if (floorId == null) {
            return Collections.emptyList();
        }
        // 1. 房间 + 床位（复用入住配置的联表查询）
        List<RoomVo> rooms = roomMapper.selectByFloorId(floorId);
        if (rooms == null || rooms.isEmpty()) {
            return Collections.emptyList();
        }
        // 1.1 床位占用兜底：老人档案没回填 bed_id 的历史数据，用「在住」的入住记录补老人信息
        fillElderFromCheckIn(rooms);

        // 2. 收集房间/床位/老人 id，同时建立 老人 -> 床位 的反查，用于把随身设备挂到床位上
        List<Long> roomIds = new ArrayList<>();
        List<Long> bedIds = new ArrayList<>();
        List<Long> elderIds = new ArrayList<>();
        Map<Long, Long> elderBedId = new HashMap<>();
        for (RoomVo room : rooms) {
            if (room.getId() != null) {
                roomIds.add(room.getId());
            }
            if (room.getBedVoList() == null) {
                room.setBedVoList(new ArrayList<>());
                continue;
            }
            for (BedVo bed : room.getBedVoList()) {
                if (bed.getId() != null) {
                    bedIds.add(bed.getId());
                }
                if (bed.getElderId() != null) {
                    elderIds.add(bed.getElderId());
                    if (bed.getId() != null) {
                        elderBedId.put(bed.getElderId(), bed.getId());
                    }
                }
            }
        }

        // 3. 一次性查出相关设备（房间固定设备 / 床位固定设备 / 老人随身设备）
        List<Device> devices = deviceMapper.selectDevicesByBinding(roomIds, bedIds, elderIds);
        if (devices == null || devices.isEmpty()) {
            // 整个楼层没有任何设备，房间都不显示
            return Collections.emptyList();
        }

        // 4. 设备数据从 Redis 取（key 固定 iot:data:list，field 为设备ID），Redis 没有的再回落数据库最新一条
        List<String> iotIds = devices.stream()
                .map(Device::getIotId)
                .filter(StringUtils::isNotEmpty)
                .distinct()
                .collect(Collectors.toList());
        Map<String, List<DeviceData>> dataByIotId = loadLatestData(iotIds);

        // 5. 按绑定位置归组：房间 -> 设备、床位 -> 设备
        Map<Long, List<DeviceVo>> roomDevices = new HashMap<>();
        Map<Long, List<DeviceVo>> bedDevices = new HashMap<>();
        for (Device device : devices) {
            Long bindingId = parseBindingId(device.getBindingLocation());
            if (bindingId == null) {
                continue;
            }
            DeviceVo deviceVo = buildDeviceVo(device, dataByIotId);
            if (Integer.valueOf(0).equals(device.getLocationType())) {
                // 随身设备：绑定的是老人ID，挂到该老人当前所在床位
                Long bedId = elderBedId.get(bindingId);
                if (bedId != null) {
                    bedDevices.computeIfAbsent(bedId, k -> new ArrayList<>()).add(deviceVo);
                }
            } else if (Integer.valueOf(1).equals(device.getPhysicalLocationType())) {
                roomDevices.computeIfAbsent(bindingId, k -> new ArrayList<>()).add(deviceVo);
            } else if (Integer.valueOf(2).equals(device.getPhysicalLocationType())) {
                bedDevices.computeIfAbsent(bindingId, k -> new ArrayList<>()).add(deviceVo);
            }
        }

        // 6. 回填到房间/床位；房间和它下面的床位都没有设备的房间不返回
        List<RoomVo> result = new ArrayList<>();
        for (RoomVo room : rooms) {
            room.setDeviceVos(roomDevices.getOrDefault(room.getId(), new ArrayList<>()));
            boolean hasDevice = !room.getDeviceVos().isEmpty();
            for (BedVo bed : room.getBedVoList()) {
                if (bed.getId() == null) {
                    continue;
                }
                bed.setDeviceVos(bedDevices.getOrDefault(bed.getId(), new ArrayList<>()));
                // 床位状态以是否有老人为准：没人就按空闲显示，避免库里残留的 bed_status=1 造成“有数据没显示”的错觉
                bed.setBedStatus(bed.getEname() == null ? 0 : 1);
                if (!bed.getDeviceVos().isEmpty()) {
                    hasDevice = true;
                }
            }
            if (hasDevice) {
                result.add(room);
            }
        }
        return result;
    }

    /**
     * 床位占用兜底
     * <p>
     * 床位上的老人姓名/老人ID 来自 elder.bed_id 的联表查询；但历史数据（直接导库、或先建入住记录后建老人档案）
     * 里 elder.bed_id 可能是空的，导致入退管理里明明有床位，智能床位页面却显示「当前床位没有安排老人」。
     * 这里用入住记录（status=0 在住）按床位号补一下，顺带把床位状态标成已入住，
     * 老人ID 补上后他身上的随身设备（如手表）也能挂到这个床位上。
     *
     * @param rooms 房间列表（就地修改床位信息）
     */
    private void fillElderFromCheckIn(List<RoomVo> rooms) {
        Set<String> bedNumbers = new HashSet<>();
        for (RoomVo room : rooms) {
            if (room.getBedVoList() == null) {
                continue;
            }
            for (BedVo bed : room.getBedVoList()) {
                if (bed.getEname() == null && StringUtils.isNotEmpty(bed.getBedNumber())) {
                    bedNumbers.add(bed.getBedNumber());
                }
            }
        }
        if (bedNumbers.isEmpty()) {
            return;
        }
        List<CheckIn> checkIns = checkInMapper.selectList(new LambdaQueryWrapper<CheckIn>()
                .eq(CheckIn::getStatus, 0)
                .in(CheckIn::getBedNumber, bedNumbers));
        if (checkIns == null || checkIns.isEmpty()) {
            return;
        }
        Map<String, CheckIn> checkInByBedNumber = new HashMap<>();
        for (CheckIn checkIn : checkIns) {
            checkInByBedNumber.putIfAbsent(checkIn.getBedNumber(), checkIn);
        }
        for (RoomVo room : rooms) {
            if (room.getBedVoList() == null) {
                continue;
            }
            for (BedVo bed : room.getBedVoList()) {
                if (bed.getEname() != null || bed.getBedNumber() == null) {
                    continue;
                }
                CheckIn checkIn = checkInByBedNumber.get(bed.getBedNumber());
                if (checkIn == null) {
                    continue;
                }
                bed.setEname(checkIn.getElderName());
                bed.setElderId(checkIn.getElderId());
                bed.setBedStatus(1);
            }
        }
    }

    /**
     * 取设备的最新上报数据
     * <p>
     * 优先从 Redis Hash（大 key：iot:data:list，小 key：设备ID）取，里面存的是设备最近一次上报的全部属性；
     * Redis 中没有（例如服务刚重启、设备还没上报过）时，回落到 device_data 里每个功能点的最新一条，保证页面有数据。
     *
     * @param iotIds 设备ID集合
     * @return 设备ID -> 该设备最新数据
     */
    private Map<String, List<DeviceData>> loadLatestData(List<String> iotIds) {
        Map<String, List<DeviceData>> dataByIotId = new HashMap<>();
        if (iotIds == null || iotIds.isEmpty()) {
            return dataByIotId;
        }
        List<String> missing = new ArrayList<>();
        List<String> cachedList = null;
        try {
            cachedList = redisTemplate.<String, String>opsForHash().multiGet(CacheConstants.IOT_DEVICE_DATA_KEY, iotIds);
        } catch (Exception e) {
            log.warn("从Redis读取设备最新数据失败，回落到数据库查询", e);
        }
        for (int i = 0; i < iotIds.size(); i++) {
            String iotId = iotIds.get(i);
            String json = (cachedList != null && i < cachedList.size()) ? cachedList.get(i) : null;
            if (StringUtils.isEmpty(json)) {
                missing.add(iotId);
                continue;
            }
            dataByIotId.put(iotId, parseDataList(json));
        }
        if (!missing.isEmpty()) {
            for (DeviceData data : deviceDataMapper.selectLatestDataByIotIds(missing)) {
                if (StringUtils.isEmpty(data.getIotId())) {
                    continue;
                }
                dataByIotId.computeIfAbsent(data.getIotId(), k -> new ArrayList<>()).add(data);
            }
        }
        return dataByIotId;
    }

    /**
     * 解析 Redis 中缓存的设备数据 JSON 数组
     */
    private List<DeviceData> parseDataList(String json) {
        try {
            List<DeviceData> list = JSONUtil.toList(json, DeviceData.class);
            return list == null ? new ArrayList<>() : list;
        } catch (Exception e) {
            log.warn("Redis中的设备数据解析失败，内容：{}", json);
            return new ArrayList<>();
        }
    }

    /**
     * 组装设备VO（设备信息 + 各功能点最新数据）
     */
    private DeviceVo buildDeviceVo(Device device, Map<String, List<DeviceData>> dataByIotId) {
        DeviceVo deviceVo = new DeviceVo();
        deviceVo.setId(device.getId());
        deviceVo.setIotId(device.getIotId());
        deviceVo.setDeviceName(device.getDeviceName());
        deviceVo.setProductKey(device.getProductKey());
        deviceVo.setProductName(device.getProductName());
        deviceVo.setBindingLocation(device.getBindingLocation());
        deviceVo.setLocationType(device.getLocationType());
        deviceVo.setPhysicalLocationType(device.getPhysicalLocationType());
        deviceVo.setDeviceDescription(device.getDeviceDescription());
        if (StringUtils.isNotEmpty(device.getIotId())) {
            deviceVo.setDeviceDataVos(dataByIotId.getOrDefault(device.getIotId(), new ArrayList<>()));
        }
        return deviceVo;
    }

    /**
     * 绑定位置在库里是字符串（房间/床位/老人ID），转成 Long，非数字返回 null
     */
    private Long parseBindingId(String bindingLocation) {
        if (StringUtils.isEmpty(bindingLocation)) {
            return null;
        }
        try {
            return Long.parseLong(bindingLocation.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

}
