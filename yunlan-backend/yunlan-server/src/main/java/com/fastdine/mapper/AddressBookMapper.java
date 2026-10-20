package com.fastdine.mapper;

import com.fastdine.entity.AddressBook;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AddressBookMapper {

    /**
     * 条件查询地址簿（列表页只传 userId）
     *
     * @param addressBook
     * @return
     */
    List<AddressBook> list(AddressBook addressBook);

    /**
     * 根据主键查询
     *
     * @param id
     * @return 查不到返回 null
     */
    @Select("select * from address_book where id = #{id}")
    AddressBook getById(Long id);

    /**
     * 查询某用户的默认地址
     *
     * @param userId
     * @return 没有默认地址时返回 null
     */
    @Select("select * from address_book where user_id = #{userId} and is_default = 1 limit 1")
    AddressBook getDefaultByUserId(Long userId);

    /**
     * 新增地址，并回填主键
     *
     * @param addressBook
     */
    void insert(AddressBook addressBook);

    /**
     * 动态修改地址（只更新非空字段）
     *
     * @param addressBook
     */
    void update(AddressBook addressBook);

    /**
     * 根据主键删除
     *
     * @param id
     */
    void deleteById(Long id);

    /**
     * 把某用户的所有地址置为非默认（设置新默认地址前调用）
     *
     * @param userId
     */
    @Update("update address_book set is_default = 0 where user_id = #{userId}")
    void updateAllToNonDefault(Long userId);
}
