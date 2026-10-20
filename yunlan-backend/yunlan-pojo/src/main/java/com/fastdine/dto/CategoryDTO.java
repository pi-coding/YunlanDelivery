package com.fastdine.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.Serializable;

@Data
public class CategoryDTO implements Serializable {

    //主键
    private Long id;

    //类型 1 菜品分类 2 套餐分类
    private Integer type;

    //分类名称
    @NotBlank(message = "分类名称不能为空")
    @Size(max = 20, message = "分类名称长度不能超过 20 位")
    private String name;

    //排序
    private Integer sort;

}
