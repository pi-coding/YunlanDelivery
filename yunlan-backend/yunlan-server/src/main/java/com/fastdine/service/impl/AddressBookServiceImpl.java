package com.fastdine.service.impl;

import com.fastdine.constant.MessageConstant;
import com.fastdine.context.BaseContext;
import com.fastdine.entity.AddressBook;
import com.fastdine.exception.AddressBookBusinessException;
import com.fastdine.mapper.AddressBookMapper;
import com.fastdine.service.AddressBookService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

@Service
@Slf4j
public class AddressBookServiceImpl implements AddressBookService {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    @Autowired
    private AddressBookMapper addressBookMapper;

    /**
     * 查询当前登录用户的地址列表
     */
    public List<AddressBook> list() {
        AddressBook query = new AddressBook();
        query.setUserId(BaseContext.getCurrentId());
        return addressBookMapper.list(query);
    }

    /**
     * 新增地址：手机号等必填项先校验；如果标了默认地址，先把历史默认地址取消
     */
    @Transactional
    public void save(AddressBook addressBook) {
        checkParam(addressBook, false);

        Long userId = BaseContext.getCurrentId();
        addressBook.setId(null);
        addressBook.setUserId(userId);
        if (addressBook.getIsDefault() == null) {
            addressBook.setIsDefault(0);
        }
        if (addressBook.getIsDefault() == 1) {
            addressBookMapper.updateAllToNonDefault(userId);
        }
        addressBookMapper.insert(addressBook);
    }

    /**
     * 修改地址（只能改自己的）
     */
    @Transactional
    public void update(AddressBook addressBook) {
        if (addressBook.getId() == null) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_NOT_FOUND);
        }
        checkParam(addressBook, true);

        Long userId = BaseContext.getCurrentId();
        getOwned(addressBook.getId(), userId);

        addressBook.setUserId(userId);
        if (addressBook.getIsDefault() != null && addressBook.getIsDefault() == 1) {
            addressBookMapper.updateAllToNonDefault(userId);
        }
        addressBookMapper.update(addressBook);
    }

    /**
     * 根据 id 查询（只能查自己的）
     */
    public AddressBook getById(Long id) {
        return getOwned(id, BaseContext.getCurrentId());
    }

    /**
     * 删除地址（只能删自己的）
     */
    public void deleteById(Long id) {
        Long userId = BaseContext.getCurrentId();
        getOwned(id, userId);
        addressBookMapper.deleteById(id);
    }

    /**
     * 设置默认地址：先把该用户全部置 0，再把目标置 1
     */
    @Transactional
    public void setDefault(Long id) {
        Long userId = BaseContext.getCurrentId();
        getOwned(id, userId);
        addressBookMapper.updateAllToNonDefault(userId);
        AddressBook update = AddressBook.builder().id(id).userId(userId).isDefault(1).build();
        addressBookMapper.update(update);
    }

    /**
     * 查询当前用户的默认地址
     */
    public AddressBook getDefault() {
        return addressBookMapper.getDefaultByUserId(BaseContext.getCurrentId());
    }

    /**
     * 查地址并校验归属，防止越权访问别人的地址
     *
     * @param id
     * @param userId
     * @return
     */
    private AddressBook getOwned(Long id, Long userId) {
        AddressBook addressBook = addressBookMapper.getById(id);
        if (addressBook == null) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_NOT_FOUND);
        }
        if (!userId.equals(addressBook.getUserId())) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_NOT_BELONG_TO_USER);
        }
        return addressBook;
    }

    /**
     * 必填项校验：
     * 新增时收货人 / 手机号 / 详细地址都必填；
     * 修改支持只传部分字段，所以只校验「传了的那几个」不被改成空值
     */
    private void checkParam(AddressBook addressBook, boolean isUpdate) {
        if (!isUpdate) {
            if (isBlank(addressBook.getConsignee()) || isBlank(addressBook.getPhone())
                    || isBlank(addressBook.getDetail())) {
                throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_PARAM_ERROR);
            }
        } else {
            if (addressBook.getConsignee() != null && isBlank(addressBook.getConsignee())
                    || addressBook.getDetail() != null && isBlank(addressBook.getDetail())) {
                throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_PARAM_ERROR);
            }
        }
        if (addressBook.getPhone() != null && !PHONE_PATTERN.matcher(addressBook.getPhone()).matches()) {
            throw new AddressBookBusinessException(MessageConstant.PHONE_FORMAT_ERROR);
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
