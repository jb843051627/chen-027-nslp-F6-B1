package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TNslpPermitBill;
import com.fc.v2.model.custom.PermitOpenSubmit;
import com.fc.v2.model.custom.PermitSignSubmit;
import com.fc.v2.service.ITNslpPermitBillService;
import com.fc.v2.shiro.util.ShiroUtils;
import com.fc.v2.util.DateUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 夜间连续施工作业许可签批单 Controller（approval-chain 形状：三关签批唯一入口）。
 *
 * 作业单位打电话口头说一声不算立单；前端没有自己新建的口子，单只从点位底册带出。
 * 报文写了第几关落库未必采信，已到几人只回显——把关的话全在服务端，本处只把
 * 登录岗与报文递过去，被拒的情形由 Service 点名抛出，全局异常处原样递回。
 *
 * @author fuce
 * @date 2026-10-06
 */
@Api(value = "夜间连续施工作业许可签批单")
@Controller
@RequestMapping("/nslpPermitBill")
public class NslpPermitBillController extends BaseController {

    private final String prefix = "admin/nslpPermitBill";

    @Autowired
    private ITNslpPermitBillService nslpPermitBillService;

    @ApiOperation(value = "签批台账跳转", notes = "签批台账跳转")
    @GetMapping("/view")
    @RequiresPermissions("nslpPermitBill:view")
    public String view() {
        return prefix + "/list";
    }

    @Log(title = "夜间连续施工作业许可签批单台账", action = "list")
    @ApiOperation(value = "签批台账", notes = "在签、点齐、止住的单照常露脸；已到几人只是回显")
    @GetMapping("/list")
    @RequiresPermissions("nslpPermitBill:list")
    @ResponseBody
    public ResultTable list(TNslpPermitBill record) {
        startPage();
        com.github.pagehelper.PageInfo<TNslpPermitBill> page =
                new com.github.pagehelper.PageInfo<TNslpPermitBill>(
                        nslpPermitBillService.selectList(new QueryWrapper<TNslpPermitBill>()
                                .eq("del_flag", 0)
                                .orderByDesc("create_time")));
        return pageTable(page.getList(), page.getTotal());
    }

    @ApiOperation(value = "按主键回查单据", notes = "行面各格是回放后的回显，凭据在逐笔留痕")
    @GetMapping("/get")
    @RequiresPermissions("nslpPermitBill:view")
    @ResponseBody
    public AjaxResult get(Long id) {
        return AjaxResult.successData(200, nslpPermitBillService.selectTNslpPermitBillById(id));
    }

    // ------------------------------------------------------------------
    // 立：只从点位底册带出，一处点位在签的只许一张
    // ------------------------------------------------------------------

    @Log(title = "签批单立单（从点位底册带出）", action = "open")
    @ApiOperation(value = "从点位底册立单", notes = "只点点位底册行；口头说一声不算，前端无新建口子")
    @PostMapping("/open")
    @RequiresPermissions("nslpPermitBill:open")
    @ResponseBody
    public AjaxResult open(PermitOpenSubmit submit) {
        if (submit.getOperator() == null || submit.getOperator().trim().isEmpty()) {
            submit.setOperator(ShiroUtils.getLoginName());
        }
        return AjaxResult.successData(200, nslpPermitBillService.open(submit));
    }

    // ------------------------------------------------------------------
    // 落笔：认可／不认可都走这一个入口，关次服务端认
    // ------------------------------------------------------------------

    @Log(title = "签批落笔", action = "sign")
    @ApiOperation(value = "落笔（唯一入口）",
            notes = "三关各数各的；报文关次不采信；同关同人第二句不覆盖不另存；不可即止住")
    @PostMapping("/sign")
    @RequiresPermissions("nslpPermitBill:sign")
    @ResponseBody
    public AjaxResult sign(PermitSignSubmit submit) {
        if (submit.getSignBy() == null || submit.getSignBy().trim().isEmpty()) {
            submit.setSignBy(ShiroUtils.getLoginName());
        }
        return toAjax(nslpPermitBillService.sign(submit) ? 1 : 0);
    }

    @Log(title = "签批单压回", action = "rollback")
    @ApiOperation(value = "压回一关", notes = "只退一关；只抹离开关本轮的笔，旧轮旧关一笔不动")
    @PostMapping("/rollback")
    @RequiresPermissions("nslpPermitBill:rollback")
    @ResponseBody
    public AjaxResult rollback(Long id, String note) {
        return AjaxResult.successData(200,
                nslpPermitBillService.rollback(id, ShiroUtils.getLoginName(), note));
    }

    // ------------------------------------------------------------------
    // 捋：追溯、受理簿对账、在签张数
    // ------------------------------------------------------------------

    @Log(title = "签批追溯", action = "trace")
    @ApiOperation(value = "追溯那一串", notes = "逐笔连同落单时刻按时序；当前关、已到几人由这串放出")
    @GetMapping("/trace")
    @RequiresPermissions("nslpPermitBill:trace")
    @ResponseBody
    public AjaxResult trace(Long id) {
        return AjaxResult.successData(200, nslpPermitBillService.trace(id));
    }

    @Log(title = "签批受理簿对账", action = "ledger")
    @ApiOperation(value = "受理簿对账", notes = "倒着数回头一关，笔数对受理簿，逐段也自洽")
    @GetMapping("/ledger")
    @RequiresPermissions("nslpPermitBill:trace")
    @ResponseBody
    public AjaxResult ledger(Long id, String day) {
        return AjaxResult.successData(200,
                nslpPermitBillService.ledger(id, DateUtils.parseDate(day)));
    }

    @ApiOperation(value = "在签张数", notes = "立单加一、点齐/止住减一；与逐段累算同源")
    @GetMapping("/countOpen")
    @RequiresPermissions("nslpPermitBill:list")
    @ResponseBody
    public AjaxResult countOpen() {
        return AjaxResult.successData(200, nslpPermitBillService.countOpen());
    }
}
