package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TNslpCaseFlow;
import com.fc.v2.model.custom.CaseIntake;
import com.fc.v2.model.custom.CaseLedger;
import com.fc.v2.model.custom.CaseSeal;
import com.fc.v2.model.custom.CaseTrace;
import com.fc.v2.service.ITNslpCaseFlowService;
import com.fc.v2.util.DateUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 噪声投诉与超标处置办件流转单 Controller（state-machine 形状：流转入口）。
 *
 * 值守岗照文办事：手机端只把字敲进来，走到哪一关后端数。被拒的情形由
 * Service 点名抛出，全局异常处原样递回，不在本处替它预设结果。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Api(value = "噪声投诉与超标处置办件流转单")
@Controller
@RequestMapping("/nslpCaseFlow")
public class NslpCaseFlowController extends BaseController {

    private final String prefix = "admin/nslpCaseFlow";

    @Autowired
    private ITNslpCaseFlowService nslpCaseFlowService;

    @ApiOperation(value = "流转台账跳转", notes = "流转台账跳转")
    @GetMapping("/view")
    @RequiresPermissions("nslpCaseFlow:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "噪声投诉与超标处置办件流转单流转台账", action = "list")
    @ApiOperation(value = "流转台账", notes = "封住、作废的件照常露脸，只是没有可动手的把手")
    @GetMapping("/list")
    @RequiresPermissions("nslpCaseFlow:list")
    @ResponseBody
    public ResultTable list(TNslpCaseFlow record) {
        QueryWrapper<TNslpCaseFlow> queryWrapper = new QueryWrapper<TNslpCaseFlow>();
        startPage();
        com.github.pagehelper.PageInfo<TNslpCaseFlow> page =
                new com.github.pagehelper.PageInfo<TNslpCaseFlow>(
                        nslpCaseFlowService.selectTNslpCaseFlowList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    // ------------------------------------------------------------------
    // 立
    // ------------------------------------------------------------------

    @Log(title = "办件受件立条", action = "intake")
    @ApiOperation(value = "受件立条", notes = "四样输入齐了才立得起；报文自带关次只作参考")
    @PostMapping("/intake")
    @RequiresPermissions("nslpCaseFlow:intake")
    @ResponseBody
    public AjaxResult intake(CaseIntake intake) {
        return AjaxResult.successData(200, nslpCaseFlowService.intake(intake));
    }

    @Log(title = "办件立起之前撤回", action = "cancelBeforeIntake")
    @ApiOperation(value = "立起之前撤回", notes = "不进主档、不占没封住名额")
    @PostMapping("/cancelBeforeIntake")
    @RequiresPermissions("nslpCaseFlow:intake")
    @ResponseBody
    public AjaxResult cancelBeforeIntake(String pointNo, String reason) {
        return toAjax(nslpCaseFlowService.cancelBeforeIntake(pointNo, reason) ? 1 : 0);
    }

    // ------------------------------------------------------------------
    // 挪
    // ------------------------------------------------------------------

    @Log(title = "办件挪前一关", action = "advance")
    @ApiOperation(value = "往前挪紧邻的下一关", notes = "越关不生效；进核查看挂名，进复查收尾看核收「可」")
    @PostMapping("/advance")
    @RequiresPermissions("nslpCaseFlow:advance")
    @ResponseBody
    public AjaxResult advance(Long id, String remark) {
        return AjaxResult.successData(200, nslpCaseFlowService.advance(id, remark));
    }

    @Log(title = "办件收回一关", action = "rollback")
    @ApiOperation(value = "收回一关", notes = "一次只收一关；头一关收无可收")
    @PostMapping("/rollback")
    @RequiresPermissions("nslpCaseFlow:rollback")
    @ResponseBody
    public AjaxResult rollback(Long id, String remark) {
        return AjaxResult.successData(200, nslpCaseFlowService.rollback(id, remark));
    }

    @Log(title = "办件本关按语", action = "note")
    @ApiOperation(value = "本关落一句按语", notes = "同一关第二句盖不上也不另存；封住后不接")
    @PostMapping("/note")
    @RequiresPermissions("nslpCaseFlow:note")
    @ResponseBody
    public AjaxResult note(Long id, String note) {
        return toAjax(nslpCaseFlowService.note(id, note) ? 1 : 0);
    }

    @Log(title = "责令关核收落笔", action = "orderAccept")
    @ApiOperation(value = "责令关核收", notes = "记成「可」，复查收尾才开得了工")
    @PostMapping("/orderAccept")
    @RequiresPermissions("nslpCaseFlow:advance")
    @ResponseBody
    public AjaxResult orderAccept(Long id, String acceptText) {
        return toAjax(nslpCaseFlowService.recordOrderAccept(id, acceptText) ? 1 : 0);
    }

    // ------------------------------------------------------------------
    // 封
    // ------------------------------------------------------------------

    @Log(title = "复查收尾封关", action = "seal")
    @ApiOperation(value = "复查收尾封关", notes = "两名见证岗各一笔＋复测凭证一串，齐了才封上")
    @PostMapping("/seal")
    @RequiresPermissions("nslpCaseFlow:seal")
    @ResponseBody
    public AjaxResult seal(CaseSeal seal) {
        return AjaxResult.successData(200, nslpCaseFlowService.seal(seal));
    }

    @Log(title = "反映人撤回视同收口", action = "withdraw")
    @ApiOperation(value = "反映人撤回", notes = "受件之后撤回，视同收口，不必再往前挪")
    @PostMapping("/withdraw")
    @RequiresPermissions("nslpCaseFlow:close")
    @ResponseBody
    public AjaxResult withdraw(Long id, String reason) {
        return AjaxResult.successData(200, nslpCaseFlowService.withdraw(id, reason));
    }

    @Log(title = "区册并走视同收口", action = "mergeZone")
    @ApiOperation(value = "区册并走收口", notes = "该册名下没封住的件一并视同收口")
    @PostMapping("/mergeZone")
    @RequiresPermissions("nslpCaseFlow:close")
    @ResponseBody
    public AjaxResult mergeZone(String zoneNo, String reason) {
        int n = nslpCaseFlowService.mergeZone(zoneNo, reason);
        return AjaxResult.success("区册" + zoneNo + "名下收口 " + n + " 条").put("closedNum", n);
    }

    @Log(title = "办件末关作废", action = "void")
    @ApiOperation(value = "末关作废", notes = "作废只从复查收尾发起；名册照常露脸，不动手")
    @PostMapping("/void")
    @RequiresPermissions("nslpCaseFlow:void")
    @ResponseBody
    public AjaxResult voidCase(Long id, String reason) {
        return toAjax(nslpCaseFlowService.voidCase(id, reason) ? 1 : 0);
    }

    // ------------------------------------------------------------------
    // 捋
    // ------------------------------------------------------------------

    @Log(title = "办件追溯", action = "trace")
    @ApiOperation(value = "追溯那一串", notes = "每关按语连同提交时刻按时序；当前档由这一串放出来")
    @GetMapping("/trace")
    @RequiresPermissions("nslpCaseFlow:trace")
    @ResponseBody
    public AjaxResult trace(Long id) {
        CaseTrace t = nslpCaseFlowService.trace(id);
        return AjaxResult.successData(200, t);
    }

    @Log(title = "受理簿对账", action = "ledger")
    @ApiOperation(value = "受理簿对账", notes = "收回再从头捋，笔数对受理簿，逐关也自洽")
    @GetMapping("/ledger")
    @RequiresPermissions("nslpCaseFlow:trace")
    @ResponseBody
    public AjaxResult ledger(Long id, String day) {
        CaseLedger l = nslpCaseFlowService.ledger(id, DateUtils.parseDate(day));
        return AjaxResult.successData(200, l);
    }

    @ApiOperation(value = "没封住件数", notes = "起一条加一、封一条减一；只认后端一次查出来的数")
    @GetMapping("/countOpen")
    @RequiresPermissions("nslpCaseFlow:list")
    @ResponseBody
    public AjaxResult countOpen() {
        return AjaxResult.successData(200, nslpCaseFlowService.countOpen());
    }
}
