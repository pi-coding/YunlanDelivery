package com.fastdine.controller.user;

import com.fastdine.entity.AddressBook;
import com.fastdine.result.Result;
import com.fastdine.service.AddressBookService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * C 端地址簿接口
 */
@RestController("userAddressBookController")
@RequestMapping("/user/addressBook")
@Api(tags = "C端-地址簿接口")
@Slf4j
public class AddressBookController {

    @Autowired
    private AddressBookService addressBookService;

    /**
     * 新增地址
     *
     * @param addressBook
     * @return
     */
    @PostMapping
    @ApiOperation("新增地址")
    public Result save(@RequestBody AddressBook addressBook) {
        log.info("新增地址：{}，{}", addressBook.getConsignee(), addressBook.getPhone());
        addressBookService.save(addressBook);
        return Result.success();
    }

    /**
     * 查询当前用户的地址列表
     *
     * @return
     */
    @GetMapping("/list")
    @ApiOperation("查询地址列表")
    public Result<List<AddressBook>> list() {
        log.info("查询地址列表");
        return Result.success(addressBookService.list());
    }

    /**
     * 查询默认地址
     *
     * @return 没有默认地址时 data 为 null
     */
    @GetMapping("/default")
    @ApiOperation("查询默认地址")
    public Result<AddressBook> getDefault() {
        log.info("查询默认地址");
        return Result.success(addressBookService.getDefault());
    }

    /**
     * 根据 id 查询地址
     *
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    @ApiOperation("根据id查询地址")
    public Result<AddressBook> getById(@PathVariable Long id) {
        log.info("根据id查询地址：{}", id);
        return Result.success(addressBookService.getById(id));
    }

    /**
     * 修改地址
     *
     * @param addressBook
     * @return
     */
    @PutMapping
    @ApiOperation("修改地址")
    public Result update(@RequestBody AddressBook addressBook) {
        log.info("修改地址：{}", addressBook.getId());
        addressBookService.update(addressBook);
        return Result.success();
    }

    /**
     * 设置默认地址
     *
     * @param addressBook body 里只需 {"id": 1}
     * @return
     */
    @PutMapping("/default")
    @ApiOperation("设置默认地址")
    public Result setDefault(@RequestBody AddressBook addressBook) {
        log.info("设置默认地址：{}", addressBook.getId());
        addressBookService.setDefault(addressBook.getId());
        return Result.success();
    }

    /**
     * 删除地址
     *
     * @param id
     * @return
     */
    @DeleteMapping
    @ApiOperation("删除地址")
    public Result delete(Long id) {
        log.info("删除地址：{}", id);
        addressBookService.deleteById(id);
        return Result.success();
    }
}
