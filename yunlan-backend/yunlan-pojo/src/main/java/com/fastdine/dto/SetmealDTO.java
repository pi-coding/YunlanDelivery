package com.fastdine.dto;

import com.fastdine.entity.SetmealDish;
import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class SetmealDTO implements Serializable {

    private Long id;

    //分类id
    @NotNull(message = "请选择套餐分类")
    private Long categoryId;

    //套餐名称
    @NotBlank(message = "套餐名称不能为空")
    @Size(max = 32, message = "套餐名称长度不能超过 32 位")
    private String name;

    //套餐价格
    @NotNull(message = "套餐价格不能为空")
    @DecimalMin(value = "0.01", message = "套餐价格必须大于 0")
    private BigDecimal price;

    //状态 0:停用 1:启用
    private Integer status;

    //描述信息
    @Size(max = 200, message = "描述信息长度不能超过 200 位")
    private String description;

    //图片
    @NotBlank(message = "请上传套餐图片")
    private String image;

    //套餐菜品关系
    private List<SetmealDish> setmealDishes = new ArrayList<>();

}
