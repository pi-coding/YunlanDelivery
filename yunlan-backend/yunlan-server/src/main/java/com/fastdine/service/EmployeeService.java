package com.fastdine.service;

import com.fastdine.dto.EmployeeDTO;
import com.fastdine.dto.EmployeeLoginDTO;
import com.fastdine.dto.EmployeePageQueryDTO;
import com.fastdine.dto.EmployeePasswordDTO;
import com.fastdine.entity.Employee;
import com.fastdine.result.PageResult;

public interface EmployeeService {

    /**
     * 员工登录
     * @param employeeLoginDTO
     * @return
     */
    Employee login(EmployeeLoginDTO employeeLoginDTO);

    /**
     * 新增员工
     * @param employeeDTO
     */
    void save(EmployeeDTO employeeDTO);

    /**
     * 分页查询
     * @param employeePageQueryDTO
     * @return
     */
    PageResult pageQuery(EmployeePageQueryDTO employeePageQueryDTO);

    /**
     * 启用禁用员工账号
     * @param status
     * @param id
     */
    void startOrStop(Integer status, Long id);

    /**
     * 根据id查询员工
     * @param id
     * @return
     */
    Employee getById(Long id);

    /**
     * 根据id修改员工信息
     * @param employeeDTO
     */
    void update(EmployeeDTO employeeDTO);

    /**
     * 修改当前登录员工的密码
     * <p>
     * 管理端「修改密码」弹窗用（课程前端有这个按钮，但课程后端没实现，
     * 不补的话点确定会 404）。
     *
     * @param passwordDTO
     */
    void editPassword(EmployeePasswordDTO passwordDTO);
}
