package com.leo.thumbbackend.controller;

import com.leo.thumbbackend.annotation.AuthCheck;
import com.leo.thumbbackend.common.BaseResponse;
import com.leo.thumbbackend.common.ResultUtils;
import com.leo.thumbbackend.model.dto.thumb.DoThumbRequest;
import com.leo.thumbbackend.service.ThumbService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("thumb")  
public class ThumbController {  
    @Resource  
    private ThumbService thumbService;  
  
    @PostMapping("/do")  
    @AuthCheck
    public BaseResponse<Boolean> doThumb(@RequestBody DoThumbRequest doThumbRequest, HttpServletRequest request) {  
        Boolean success = thumbService.doThumb(doThumbRequest, request);  
        return ResultUtils.success(success);  
    }

    @PostMapping("/undo")
    @AuthCheck
    public BaseResponse<Boolean> undoThumb(@RequestBody DoThumbRequest doThumbRequest, HttpServletRequest request) {
        Boolean success = thumbService.undoThumb(doThumbRequest, request);
        return ResultUtils.success(success);
    }

}
