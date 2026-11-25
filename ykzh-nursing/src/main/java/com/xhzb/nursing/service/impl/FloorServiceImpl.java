package com.xhzb.nursing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xhzb.nursing.domain.Bed;
import com.xhzb.nursing.domain.Floor;
import com.xhzb.nursing.domain.Room;
import com.xhzb.nursing.mapper.BedMapper;
import com.xhzb.nursing.mapper.FloorMapper;
import com.xhzb.nursing.mapper.RoomMapper;
import com.xhzb.nursing.service.IFloorService;
import com.xhzb.nursing.domain.vo.BedVo;
import com.xhzb.nursing.domain.vo.CascaderNodeVo;
import com.xhzb.nursing.domain.vo.FloorVo;
import com.xhzb.nursing.domain.vo.RoomVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 楼层Service业务层处理
 * 
 * @author ruoyi
 * @date 2025-03-28
 */
@Service
public class FloorServiceImpl extends ServiceImpl<FloorMapper, Floor> implements IFloorService
{
    @Autowired
    private FloorMapper floorMapper;
    @Autowired
    private RoomMapper roomMapper;
    @Autowired
    private BedMapper bedMapper;

    /**
     * 查询楼层
     * 
     * @param id 楼层主键
     * @return 楼层
     */
    @Override
    public Floor selectFloorById(Long id)
    {
        return getById(id);
    }

    /**
     * 新增楼层
     * 
     * @param floor 楼层
     * @return 结果
     */
    @Override
    public int insertFloor(Floor floor)
    {
        return save(floor) ? 1 : 0;
    }

    /**
     * 修改楼层
     * 
     * @param floor 楼层
     * @return 结果
     */
    @Override
    public int updateFloor(Floor floor)
    {
        return updateById(floor) ? 1 : 0;
    }

    /**
     * 批量删除楼层
     * 
     * @param ids 需要删除的楼层主键
     * @return 结果
     */
    @Override
    public int deleteFloorByIds(Long[] ids)
    {
        return removeByIds(Arrays.asList(ids)) ? 1 : 0;
    }

    /**
     * 删除楼层信息
     * 
     * @param id 楼层主键
     * @return 结果
     */
    @Override
    public int deleteFloorById(Long id)
    {
        return removeById(id) ? 1 : 0;
    }


    /**
     * 查询负责老人楼层
     * @return
     */
    @Override
    public List<Floor> selectAllByNur() {
        return floorMapper.selectAllByNur();
    }

    /**
     * 按床位状态查询楼层-房间-床位三级树（入住选床使用）
     * <p>
     * 返回 {value,label,children} 结构的级联节点：前端 el-cascader 默认按这三个字段渲染，
     * device 页面也按这三个字段遍历。只保留有匹配床位的房间和有房间的楼层。
     *
     * @param bedStatus 床位状态（0=空闲 1=已入住 2=已退住），为 null 时不过滤
     * @return 楼层级联树
     */
    @Override
    public List<CascaderNodeVo> selectFloorsWithRoomsAndBeds(Integer bedStatus) {
        List<Floor> floors = this.list(new LambdaQueryWrapper<Floor>().orderByAsc(Floor::getCode));
        if (floors.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> floorIds = floors.stream().map(Floor::getId).collect(Collectors.toList());

        List<Room> rooms = roomMapper.selectList(new LambdaQueryWrapper<Room>()
                .in(Room::getFloorId, floorIds)
                .orderByAsc(Room::getSort));
        if (rooms.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> roomIds = rooms.stream().map(Room::getId).collect(Collectors.toList());

        LambdaQueryWrapper<Bed> bedWrapper = new LambdaQueryWrapper<Bed>()
                .in(Bed::getRoomId, roomIds)
                .orderByAsc(Bed::getSort);
        if (bedStatus != null) {
            bedWrapper.eq(Bed::getBedStatus, bedStatus);
        }
        List<Bed> beds = bedMapper.selectList(bedWrapper);
        if (beds.isEmpty()) {
            return new ArrayList<>();
        }

        // 床位节点按房间分组
        Map<Long, List<CascaderNodeVo>> bedMap = new HashMap<>();
        for (Bed bed : beds) {
            CascaderNodeVo bedNode = new CascaderNodeVo();
            bedNode.setValue(String.valueOf(bed.getId()));
            bedNode.setLabel(bed.getBedNumber());
            bedMap.computeIfAbsent(bed.getRoomId(), k -> new ArrayList<>()).add(bedNode);
        }

        // 房间节点按楼层分组（只保留有床位的房间）
        Map<Long, List<CascaderNodeVo>> roomMap = new HashMap<>();
        for (Room room : rooms) {
            List<CascaderNodeVo> bedNodes = bedMap.get(room.getId());
            if (bedNodes == null || bedNodes.isEmpty()) {
                continue;
            }
            CascaderNodeVo roomNode = new CascaderNodeVo();
            roomNode.setValue(String.valueOf(room.getId()));
            roomNode.setLabel(room.getCode());
            roomNode.setChildren(bedNodes);
            roomMap.computeIfAbsent(room.getFloorId(), k -> new ArrayList<>()).add(roomNode);
        }

        // 楼层节点（只保留有房间的楼层）
        List<CascaderNodeVo> result = new ArrayList<>();
        for (Floor floor : floors) {
            List<CascaderNodeVo> roomNodes = roomMap.get(floor.getId());
            if (roomNodes == null || roomNodes.isEmpty()) {
                continue;
            }
            CascaderNodeVo floorNode = new CascaderNodeVo();
            floorNode.setValue(String.valueOf(floor.getId()));
            floorNode.setLabel(floor.getName());
            floorNode.setChildren(roomNodes);
            result.add(floorNode);
        }
        return result;
    }

    /**
     * 查询智能楼层
     * @return
     */
    @Override
    public List<FloorVo> getAllFloorWithDevice() {
        return floorMapper.getAllFloorsWithDevice();
    }
}
