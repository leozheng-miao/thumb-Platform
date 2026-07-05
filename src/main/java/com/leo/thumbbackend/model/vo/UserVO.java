package com.leo.thumbbackend.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class UserVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String userAccount;
}
