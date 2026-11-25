package com.xhzb.nursing.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xhzb.nursing.domain.Room;
import com.xhzb.nursing.domain.vo.RoomVo;

import java.util.List;

/**
 * 房间Service接口
 * 
 * @author ruoyi
 * @date 2025-03-28
 */
public interface IRoomService extends IService<Room>
{
    /**
     * 查询房间
     * 
     * @param id 房间主键
     * @return 房间
     */
    public Room selectRoomById(Long id);

    /**
     * 查询房间列表
     * 
     * @param room 房间
     * @return 房间集合
     */
    public List<Room> selectRoomList(Room room);

    /**
     * 新增房间
     * 
     * @param room 房间
     * @return 结果
     */
    public int insertRoom(Room room);

    /**
     * 修改房间
     * 
     * @param room 房间
     * @return 结果
     */
    public int updateRoom(Room room);

    /**
     * 批量删除房间
     * 
     * @param ids 需要删除的房间主键集合
     * @return 结果
     */
    public int deleteRoomByIds(Long[] ids);

    /**
     * 根据楼层 id 获取房间视图对象列表
     * @param floorId
     * @return
     */
    List<RoomVo> getRoomsByFloorId(Long floorId);

    /**
     * 获取所有房间（负责老人）
     * @param floorId
     * @return
     */
    List<RoomVo> getRoomsWithNurByFloorId(Long floorId);

    /**
     * 获取单个房间的视图对象（含楼层名、房型价格、房间编号）
     *
     * @param id 房间ID
     * @return 房间视图对象，不存在时返回 null
     */
    RoomVo selectRoomVoById(Long id);

    /**
     * 获取楼层下所有房间及其绑定设备（智能床位）
     * <p>
     * 房间上挂绑定该房间的固定设备；床位上挂绑定该床位的固定设备，以及该床位老人身上的随身设备；
     * 每个设备都带上各功能点的最新上报数据。
     *
     * @param floorId 楼层ID，为空时返回空集合
     * @return 房间视图对象集合
     */
    List<RoomVo> getRoomsWithDeviceByFloorId(Long floorId);

}
