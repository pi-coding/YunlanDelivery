package com.fastdine.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.fastdine.constant.MessageConstant;
import com.fastdine.constant.PasswordConstant;
import com.fastdine.constant.StatusConstant;
import com.fastdine.context.BaseContext;
import com.fastdine.dto.EmployeeDTO;
import com.fastdine.dto.EmployeeLoginDTO;
import com.fastdine.dto.EmployeePageQueryDTO;
import com.fastdine.dto.EmployeePasswordDTO;
import com.fastdine.entity.Employee;
import com.fastdine.exception.AccountLockedException;
import com.fastdine.exception.AccountNotFoundException;
import com.fastdine.exception.PasswordEditFailedException;
import com.fastdine.exception.PasswordErrorException;
import com.fastdine.mapper.EmployeeMapper;
import com.fastdine.result.PageResult;
import com.fastdine.service.EmployeeService;
import com.fastdine.utils.PageQueryUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.util.List;

@Service
public class EmployeeServiceImpl implements EmployeeService {

    @Autowired
    private EmployeeMapper employeeMapper;

    /**
     * 员工登录
     *
     * @param employeeLoginDTO
     * @return
     */
    public Employee login(EmployeeLoginDTO employeeLoginDTO) {
        String username = employeeLoginDTO.getUsername();
        String password = employeeLoginDTO.getPassword();

        //1、根据用户名查询数据库中的数据
        Employee employee = employeeMapper.getByUsername(username);

        //2、处理各种异常情况（用户名不存在、密码不对、账号被锁定）
        if (employee == null) {
            //账号不存在
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        //密码比对：对前端传过来的明文密码进行md5加密处理
        password = DigestUtils.md5DigestAsHex(password.getBytes());
        if (!password.equals(employee.getPassword())) {
            //密码错误
            throw new PasswordErrorException(MessageConstant.PASSWORD_ERROR);
        }

        if (employee.getStatus() == StatusConstant.DISABLE) {
            //账号被锁定
            throw new AccountLockedException(MessageConstant.ACCOUNT_LOCKED);
        }

        //3、返回实体对象
        return employee;
    }

    /**
     * 新增员工
     *
     * @param employeeDTO
     */
    public void save(EmployeeDTO employeeDTO) {
        Employee employee = new Employee();
        BeanUtils.copyProperties(employeeDTO, employee);

        //设置账号的状态，默认正常状态 1表示正常 0表示锁定
        employee.setStatus(StatusConstant.ENABLE);

        //设置密码，默认密码 123456
        employee.setPassword(DigestUtils.md5DigestAsHex(PasswordConstant.DEFAULT_PASSWORD.getBytes()));

        //创建时间、创建人、修改时间、修改人由 AutoFillAspect 自动填充
        employeeMapper.insert(employee);
    }

    /**
     * 分页查询
     *
     * @param employeePageQueryDTO
     * @return
     */
    public PageResult pageQuery(EmployeePageQueryDTO employeePageQueryDTO) {
        PageHelper.startPage(PageQueryUtil.page(employeePageQueryDTO.getPage()),
                PageQueryUtil.pageSize(employeePageQueryDTO.getPageSize()));

        Page<Employee> page = employeeMapper.pageQuery(employeePageQueryDTO);

        long total = page.getTotal();
        List<Employee> records = page.getResult();

        return new PageResult(total, records);
    }

    /**
     * 启用禁用员工账号
     *
     * @param status
     * @param id
     */
    public void startOrStop(Integer status, Long id) {
        //修改时间、修改人由 AutoFillAspect 自动填充
        Employee employee = Employee.builder()
                .status(status)
                .id(id)
                .build();

        employeeMapper.update(employee);
    }

    /**
     * 根据id查询员工
     *
     * @param id
     * @return
     */
    public Employee getById(Long id) {
        Employee employee = employeeMapper.getById(id);
        if (employee != null) {
            employee.setPassword("****");
        }
        return employee;
    }

    /**
     * 根据id修改员工信息
     *
     * @param employeeDTO
     */
    public void update(EmployeeDTO employeeDTO) {
        Employee employee = new Employee();
        BeanUtils.copyProperties(employeeDTO, employee);

        //修改时间、修改人由 AutoFillAspect 自动填充
        employeeMapper.update(employee);
    }

    /**
     * 修改当前登录员工的密码
     *
     * @param passwordDTO
     */
    public void editPassword(EmployeePasswordDTO passwordDTO) {
        //1、两次输入的新密码必须一致
        if (!passwordDTO.getNewPassword().equals(passwordDTO.getAffirmPassword())) {
            throw new PasswordEditFailedException(MessageConstant.PASSWORD_MISMATCH);
        }

        //2、只能改自己的密码，id 从 token 里取，不接受前端传
        Long currentId = BaseContext.getCurrentId();
        Employee employee = employeeMapper.getById(currentId);
        if (employee == null) {
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        //3、校验原密码
        String oldPasswordMd5 = DigestUtils.md5DigestAsHex(passwordDTO.getOldPassword().getBytes());
        if (!oldPasswordMd5.equals(employee.getPassword())) {
            throw new PasswordEditFailedException(MessageConstant.OLD_PASSWORD_ERROR);
        }

        //4、新密码不能和原密码相同
        if (passwordDTO.getNewPassword().equals(passwordDTO.getOldPassword())) {
            throw new PasswordEditFailedException(MessageConstant.PASSWORD_EDIT_FAILED);
        }

        //5、更新密码（md5），修改人/时间由切面填充
        Employee update = new Employee();
        update.setId(currentId);
        update.setPassword(DigestUtils.md5DigestAsHex(passwordDTO.getNewPassword().getBytes()));
        employeeMapper.update(update);
    }
}
