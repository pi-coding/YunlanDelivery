package com.fastdine.mapper;

import com.fastdine.entity.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

@Mapper
public interface UserMapper {

    /**
     * 根据微信 openid 查询用户
     *
     * @param openid
     * @return 查不到返回 null
     */
    @Select("select * from user where openid = #{openid} limit 1")
    User getByOpenid(String openid);

    /**
     * 根据主键查询用户（支付时需要拿 openid）
     *
     * @param id
     * @return 查不到返回 null
     */
    @Select("select * from user where id = #{id}")
    User getById(Long id);

    /**
     * 统计用户数（begin / end 按 create_time 过滤）
     *
     * @param map 可只传 end（累计用户）或同时传 begin+end（新增用户）
     * @return
     */
    @Select({"<script>",
            "select count(id) from user",
            "<where>",
            "<if test='begin != null'>and create_time &gt;= #{begin}</if>",
            "<if test='end != null'>and create_time &lt;= #{end}</if>",
            "</where>",
            "</script>"})
    Integer countByMap(Map<String, Object> map);

    /**
     * 新增用户（微信首次登录时自动注册），并回填主键
     *
     * @param user
     */
    @Insert("insert into user (openid, name, phone, sex, id_number, avatar, create_time) " +
            "values (#{openid}, #{name}, #{phone}, #{sex}, #{idNumber}, #{avatar}, #{createTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(User user);
}
