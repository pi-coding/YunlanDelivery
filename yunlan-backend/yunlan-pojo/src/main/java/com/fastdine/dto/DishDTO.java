package com.fastdine.dto;

import com.fastdine.entity.DishFlavor;
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
public class DishDTO implements Serializable {

    private Long id;
    //菜品名称
    @NotBlank(message = "菜品名称不能为空")
    @Size(max = 32, message = "菜品名称长度不能超过 32 位")
    private String name;
    //菜品分类id
    @NotNull(message = "请选择菜品分类")
    private Long categoryId;
    //菜品价格
    @NotNull(message = "菜品价格不能为空")
    @DecimalMin(value = "0.01", message = "菜品价格必须大于 0")
    private BigDecimal price;
    //图片
    @NotBlank(message = "请上传菜品图片")
    private String image;
    //描述信息
    @Size(max = 200, message = "描述信息长度不能超过 200 位")
    private String description;
    //0 停售 1 起售
    private Integer status;
    //口味
    private List<DishFlavor> flavors = new ArrayList<>();

}
