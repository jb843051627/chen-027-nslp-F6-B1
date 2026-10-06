package com.fc.v2.model.custom;

import java.io.Serializable;

import com.fc.v2.service.support.TaskDispatchRules;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 安排单走一趟的回话：挑中几行、发出几行、几行没成，连同那句答话。
 *
 * 三个数与逐行落下的记号在同一次走表里算出；答话只由这三个数放出来
 * （{@link TaskDispatchRules#replyOf}），页面上那一屏只是转述，不许各数各的、
 * 也不许另起一轮逐行数来对冲。
 *
 * @author fuce
 */
@ApiModel(value = "TaskDispatchResult", description = "安排单走一趟的回话（三个数＋一句答话）")
public class TaskDispatchResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 本趟挑中几行（定额 500 装满即止，够不着的下趟接着挑） */
    @ApiModelProperty(value = "本趟挑中几行")
    private int picked;

    /** 本趟发出几行（情形改已发出、记要补上发出那一刻与发给谁，同一句话落下） */
    @ApiModelProperty(value = "本趟发出几行")
    private int sent;

    /** 本趟几行没成（本行标一句失败原由，其余各行照常办完） */
    @ApiModelProperty(value = "本趟几行没成")
    private int failed;

    /** 趟末答话：只由上面三个数放出来；没动单时只回「本轮未改动安排单」 */
    @ApiModelProperty(value = "趟末答话")
    private String reply;

    /**
     * 唯一装配口：只收这一趟算出的三个数，答话由它们放出来，
     * 不许先把数摆出来再回头找依据。
     */
    public static TaskDispatchResult of(int picked, int sent, int failed) {
        TaskDispatchResult r = new TaskDispatchResult();
        r.picked = picked;
        r.sent = sent;
        r.failed = failed;
        r.reply = TaskDispatchRules.replyOf(picked, sent, failed);
        return r;
    }

    /** 这一趟到底动没动单：只引用趟末那三个数 */
    public boolean isModified() {
        return TaskDispatchRules.modified(sent, failed);
    }

    public int getPicked() {
        return picked;
    }

    public int getSent() {
        return sent;
    }

    public int getFailed() {
        return failed;
    }

    public String getReply() {
        return reply;
    }
}
