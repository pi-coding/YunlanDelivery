package com.fastdine.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.fastdine.constant.MessageConstant;
import com.fastdine.constant.StatusConstant;
import com.fastdine.dto.SetmealDTO;
import com.fastdine.dto.SetmealPageQueryDTO;
import com.fastdine.entity.Dish;
import com.fastdine.entity.Setmeal;
import com.fastdine.entity.SetmealDish;
import com.fastdine.exception.DeletionNotAllowedException;
import com.fastdine.exception.SetmealEnableFailedException;
import com.fastdine.mapper.DishMapper;
import com.fastdine.mapper.SetmealDishMapper;
import com.fastdine.mapper.SetmealMapper;
import com.fastdine.result.PageResult;
import com.fastdine.service.SetmealService;
import com.fastdine.utils.PageQueryUtil;
import com.fastdine.vo.DishItemVO;
import com.fastdine.vo.SetmealVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
public class SetmealServiceImpl implements SetmealService {

    @Autowired
    private SetmealMapper setmealMapper;
    @Autowired
    private SetmealDishMapper setmealDishMapper;
    @Autowired
    private DishMapper dishMapper;

    /**
     * 新增套餐，同时保存套餐和菜品的关联关系
     *
     * @param setmealDTO
     */
    @Transactional
    public void saveWithDish(SetmealDTO setmealDTO) {
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);

        //向套餐表插入数据（创建人、创建时间由 AutoFillAspect 自动填充）
        setmealMapper.insert(setmeal);

        //获取 insert 语句生成的主键值
        Long setmealId = setmeal.getId();

        //保存套餐和菜品的关联关系
        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        if (setmealDishes != null && !setmealDishes.isEmpty()) {
            setmealDishes.forEach(setmealDish -> setmealDish.setSetmealId(setmealId));
            setmealDishMapper.insertBatch(setmealDishes);
        }
    }

    /**
     * 套餐分页查询
     *
     * @param setmealPageQueryDTO
     * @return
     */
    public PageResult pageQuery(SetmealPageQueryDTO setmealPageQueryDTO) {
        PageHelper.startPage(PageQueryUtil.page(setmealPageQueryDTO.getPage()),
                PageQueryUtil.pageSize(setmealPageQueryDTO.getPageSize()));
        Page<SetmealVO> page = setmealMapper.pageQuery(setmealPageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 套餐批量删除
     *
     * @param ids
     */
    @Transactional
    public void deleteBatch(List<Long> ids) {
        //1、起售中的套餐不能删除
        for (Long id : ids) {
            Setmeal setmeal = setmealMapper.getById(id);
            if (setmeal != null && StatusConstant.ENABLE.equals(setmeal.getStatus())) {
                throw new DeletionNotAllowedException(MessageConstant.SETMEAL_ON_SALE);
            }
        }

        //2、删除套餐数据，同时删除套餐与菜品的关联关系
        for (Long setmealId : ids) {
            setmealMapper.deleteById(setmealId);
            setmealDishMapper.deleteBySetmealId(setmealId);
        }
    }

    /**
     * 根据id查询套餐和关联的菜品数据
     *
     * @param id
     * @return
     */
    public SetmealVO getByIdWithDish(Long id) {
        return setmealMapper.getByIdWithDish(id);
    }

    /**
     * 修改套餐
     *
     * @param setmealDTO
     */
    @Transactional
    public void update(SetmealDTO setmealDTO) {
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);

        //1、修改套餐表基本信息（修改人、修改时间由 AutoFillAspect 自动填充）
        setmealMapper.update(setmeal);

        Long setmealId = setmealDTO.getId();

        //2、删除原有套餐与菜品的关联关系后重新插入
        setmealDishMapper.deleteBySetmealId(setmealId);

        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        if (setmealDishes != null && !setmealDishes.isEmpty()) {
            setmealDishes.forEach(setmealDish -> setmealDish.setSetmealId(setmealId));
            setmealDishMapper.insertBatch(setmealDishes);
        }
    }

    /**
     * 套餐起售、停售
     *
     * @param status
     * @param id
     */
    public void startOrStop(Integer status, Long id) {
        //起售套餐时，套餐内如果有停售的菜品，不允许起售
        if (StatusConstant.ENABLE.equals(status)) {
            List<Dish> dishList = dishMapper.getBySetmealId(id);
            if (dishList != null) {
                for (Dish dish : dishList) {
                    if (StatusConstant.DISABLE.equals(dish.getStatus())) {
                        throw new SetmealEnableFailedException(MessageConstant.SETMEAL_ENABLE_FAILED);
                    }
                }
            }
        }

        Setmeal setmeal = Setmeal.builder()
                .id(id)
                .status(status)
                .build();
        setmealMapper.update(setmeal);
    }

    /**
     * 动态条件查询套餐
     *
     * @param setmeal
     * @return
     */
    public List<Setmeal> list(Setmeal setmeal) {
        return setmealMapper.list(setmeal);
    }

    /**
     * 根据套餐id查询包含的菜品列表
     *
     * @param id
     * @return
     */
    public List<DishItemVO> getDishItemById(Long id) {
        return setmealMapper.getDishItemBySetmealId(id);
    }
}
