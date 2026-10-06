package com.fc.v2.controller.admin;

import java.util.List;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TNslpPoint;
import com.fc.v2.model.custom.LedgerQuery;
import com.fc.v2.model.custom.LedgerView;
import com.fc.v2.model.custom.PointArchiveSubmit;
import com.fc.v2.service.ITNslpPointLedgerService;
import com.fc.v2.service.ITNslpPointService;
import com.fc.v2.shiro.util.ShiroUtils;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

/**
 * 声管岗点位建档·三态·名册 Controller。
 *
 * 屏幕上看到的那一行和库里的那一行必须是同一行：取值、步子、处数都只由服务端给，
 * 页面不再算第二遍。
 *
 * @author fuce
 * @date 2026-10-06
 */
@Api(value = "声管岗点位建档与名册")
@Controller
@RequestMapping("/NslpPointArchiveController")
public class NslpPointArchiveController {

    @Autowired
    private ITNslpPointService nslpPointService;

    @Autowired
    private ITNslpPointLedgerService nslpPointLedgerService;

    private String operator(PointArchiveSubmit submit) {
        // 落笔人只认登录这一位；报文里没带就由服务端按当前登录岗补，复核点得出人
        String op = submit.getOperator();
        if (op == null || op.trim().isEmpty()) {
            op = ShiroUtils.getLoginName();
            submit.setOperator(op);
        }
        return op;
    }

    @Log(title = "点位建档（五道筛+唯一轧法）", action = "archive")
    @ApiOperation(value = "建档/改线", notes = "建档岗逐条建；复测带出也走这里。过五道筛，lack 服务端重轧")
    @PostMapping("/archive")
    @RequiresPermissions("nslp:nslpPoint:add")
    @ResponseBody
    public AjaxResult archive(@RequestBody PointArchiveSubmit submit) {
        operator(submit);
        TNslpPoint row = nslpPointService.archive(submit);
        return AjaxResult.successData(200, row);
    }

    @Log(title = "已达标次数更正（第二趟重轧）", action = "correctGot")
    @ApiOperation(value = "更正达标次数", notes = "got 更正后 lack 当场重轧盖写")
    @PostMapping("/correctGot")
    @RequiresPermissions("nslp:nslpPoint:edit")
    @ResponseBody
    public AjaxResult correctGot(Long id, Integer got) {
        return AjaxResult.successData(200,
                nslpPointService.correctGot(id, got, ShiroUtils.getLoginName()));
    }

    @Log(title = "点位改挂（第三趟重轧）", action = "rezone")
    @ApiOperation(value = "改挂区册", notes = "新册须在册未撤出；lack 第三趟重轧；压卡行动不得")
    @PostMapping("/rezone")
    @RequiresPermissions("nslp:nslpPoint:edit")
    @ResponseBody
    public AjaxResult rezone(Long id, String zoneNo) {
        return AjaxResult.successData(200,
                nslpPointService.rezone(id, zoneNo, ShiroUtils.getLoginName()));
    }

    @Log(title = "三态推进", action = "advanceFlow")
    @ApiOperation(value = "推进一步", notes = "待测→已测；步子只由留痕回放")
    @PostMapping("/advance")
    @RequiresPermissions("nslp:nslpPoint:edit")
    @ResponseBody
    public AjaxResult advance(Long id, String note) {
        return AjaxResult.successData(200,
                nslpPointService.advanceFlow(id, ShiroUtils.getLoginName(), note));
    }

    @Log(title = "三态退回", action = "rollbackFlow")
    @ApiOperation(value = "退回一步", notes = "已测→待测；先前留痕不抹")
    @PostMapping("/rollback")
    @RequiresPermissions("nslp:nslpPoint:edit")
    @ResponseBody
    public AjaxResult rollback(Long id, String note) {
        return AjaxResult.successData(200,
                nslpPointService.rollbackFlow(id, ShiroUtils.getLoginName(), note));
    }

    @Log(title = "三态压卡", action = "sealFlow")
    @ApiOperation(value = "压卡", notes = "到头：添不进动不得撤不掉，名额腾出")
    @PostMapping("/seal")
    @RequiresPermissions("nslp:nslpPoint:edit")
    @ResponseBody
    public AjaxResult seal(Long id, String note) {
        return AjaxResult.successData(200,
                nslpPointService.sealFlow(id, ShiroUtils.getLoginName(), note));
    }

    @Log(title = "就地叫停", action = "stopFlow")
    @ApiOperation(value = "叫停", notes = "腾名额但行仍在册，不是压卡")
    @PostMapping("/stop")
    @RequiresPermissions("nslp:nslpPoint:edit")
    @ResponseBody
    public AjaxResult stop(Long id, String note) {
        return AjaxResult.successData(200,
                nslpPointService.stopFlow(id, ShiroUtils.getLoginName(), note));
    }

    @ApiOperation(value = "当下步（回放留痕）", notes = "只由 t_nslp_point_step 数出")
    @GetMapping("/stage")
    @RequiresPermissions("nslp:nslpPoint:view")
    @ResponseBody
    public AjaxResult stage(Long id) {
        return AjaxResult.successData(200, nslpPointService.currentStage(id));
    }

    @Log(title = "名册一种摆法", action = "ledgerView")
    @ApiOperation(value = "名册摆法", notes = "byKind/byZone/byIntake；底数、分页、处数只由服务端给")
    @PostMapping("/ledger/view")
    @RequiresPermissions("nslp:nslpPoint:list")
    @ResponseBody
    public AjaxResult ledgerView(@RequestBody LedgerQuery query, String view) {
        LedgerView v = nslpPointLedgerService.view(query, view);
        return AjaxResult.successData(200, v);
    }

    @Log(title = "名册三种摆法（同源处数）", action = "ledgerThree")
    @ApiOperation(value = "三种摆法", notes = "同一年度同一批行三个总数必须相等")
    @PostMapping("/ledger/three")
    @RequiresPermissions("nslp:nslpPoint:list")
    @ResponseBody
    public AjaxResult ledgerThree(@RequestBody LedgerQuery query) {
        List<LedgerView> views = nslpPointLedgerService.threeViews(query);
        return AjaxResult.successData(200, views);
    }

    @ApiOperation(value = "没落笔人的行", notes = "建/改两处都指不出人，按没办完单列")
    @PostMapping("/ledger/unattributed")
    @RequiresPermissions("nslp:nslpPoint:list")
    @ResponseBody
    public AjaxResult unattributed(@RequestBody LedgerQuery query) {
        return AjaxResult.successData(200, nslpPointLedgerService.listUnattributed(query));
    }

    @ApiOperation(value = "区册全称对不齐的行", notes = "以区册为准的缺陷清单，不连带清行")
    @GetMapping("/ledger/zoneNameMismatch")
    @RequiresPermissions("nslp:nslpPoint:list")
    @ResponseBody
    public AjaxResult zoneNameMismatch(Integer regYear) {
        return AjaxResult.successData(200, nslpPointLedgerService.listZoneNameMismatches(regYear));
    }
}
