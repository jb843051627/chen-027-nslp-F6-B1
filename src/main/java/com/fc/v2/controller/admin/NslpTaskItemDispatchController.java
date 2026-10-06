package com.fc.v2.controller.admin;

import java.util.Date;

import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.log.Log;
import com.fc.v2.service.ITNslpTaskItemDispatchService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 复测与巡查安排单夜间走表 Controller（dispatch-job 形状：唯一触发把手）。
 *
 * 整张安排单（复测通知与现场巡查同在一张单上）夜里走一趟，触发把手只有
 * /nslpTaskItemDispatch/run 这一个，不留第二个。02:00–05:00 那一截之外按下，
 * 程序只答话、不动单上任何一行；连按几回，答话一字不差：本轮未改动安排单。
 * 趟末那三个数由走表那一遍给出，本处只是转述，不另起一轮逐行数来对冲。
 *
 * @author fuce
 * @date 2026-10-06
 */
@Api(value = "复测与巡查安排单夜间走表")
@Controller
@RequestMapping("/nslpTaskItemDispatch")
public class NslpTaskItemDispatchController extends BaseController {

    @Autowired
    private ITNslpTaskItemDispatchService nslpTaskItemDispatchService;

    @Log(title = "安排单夜间走表", action = "runOnce")
    @ApiOperation(value = "走一趟（唯一触发把手）",
            notes = "只认 02:00–05:00；窗外按下只答话不动单：本轮未改动安排单；一趟至多派 500 行")
    @PostMapping("/run")
    @RequiresPermissions("nslpTaskItemDispatch:run")
    @ResponseBody
    public AjaxResult run() {
        return AjaxResult.successData(200, nslpTaskItemDispatchService.runOnce(new Date()));
    }
}
