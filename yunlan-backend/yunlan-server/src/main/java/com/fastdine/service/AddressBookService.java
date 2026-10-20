package com.fastdine.service;

import com.fastdine.entity.AddressBook;

import java.util.List;

public interface AddressBookService {

    /**
     * 查询当前登录用户的地址列表
     *
     * @return
     */
    List<AddressBook> list();

    /**
     * 新增地址
     *
     * @param addressBook
     */
    void save(AddressBook addressBook);

    /**
     * 修改地址
     *
     * @param addressBook
     */
    void update(AddressBook addressBook);

    /**
     * 根据 id 查询（只能查自己的）
     *
     * @param id
     * @return
     */
    AddressBook getById(Long id);

    /**
     * 删除地址（只能删自己的）
     *
     * @param id
     */
    void deleteById(Long id);

    /**
     * 设置默认地址
     *
     * @param id
     */
    void setDefault(Long id);

    /**
     * 查询当前用户的默认地址
     *
     * @return 没有则返回 null
     */
    AddressBook getDefault();
}
