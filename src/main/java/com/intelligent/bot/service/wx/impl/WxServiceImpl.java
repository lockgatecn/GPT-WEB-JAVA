package com.intelligent.bot.service.wx.impl;

import cn.hutool.core.lang.Validator;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.extra.servlet.ServletUtil;
import com.intelligent.bot.api.midjourney.support.TaskCondition;
import com.intelligent.bot.api.sys.AuthController;
import com.intelligent.bot.base.exception.E;
import com.intelligent.bot.constant.CommonConst;
import com.intelligent.bot.enums.mj.TaskAction;
import com.intelligent.bot.enums.mj.TaskStatus;
import com.intelligent.bot.model.*;
import com.intelligent.bot.model.res.sys.UserAuthRes;
import com.intelligent.bot.server.SseEmitterServer;
import com.intelligent.bot.service.baidu.BaiDuService;
import com.intelligent.bot.service.mj.TaskService;
import com.intelligent.bot.service.mj.TaskStoreService;
import com.intelligent.bot.service.sys.*;
import com.intelligent.bot.service.wx.WxService;
import com.intelligent.bot.utils.sys.IDUtil;
import com.intelligent.bot.utils.sys.PasswordUtil;
import com.intelligent.bot.utils.sys.RedisUtil;
import lombok.extern.log4j.Log4j2;
import me.chanjar.weixin.mp.bean.message.WxMpXmlMessage;
import me.chanjar.weixin.mp.bean.message.WxMpXmlOutMessage;
import me.chanjar.weixin.mp.bean.message.WxMpXmlOutTextMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


@Service("WxService")
@Log4j2
@Transactional(rollbackFor = Exception.class)
public class WxServiceImpl implements WxService {

    @Resource
    IUserService userService;
    @Resource
    IWxLogService wxLogService;

    @Resource
    TaskService taskService;

    @Resource
    BaiDuService baiDuService;

    @Resource
    CheckService checkService;

    @Resource
    TaskStoreService taskStoreService;

    @Resource
    ICardPinService cardPinService;

    @Resource
    IOrderService orderService;


    @Override
    @Transactional(rollbackFor = E.class)
    public String callbackEvent(HttpServletRequest request) throws Exception {
        SysConfig sysConfig = RedisUtil.getCacheObject(CommonConst.SYS_CONFIG);
        String url = sysConfig.getClientUrl();
        // 默认返回的文本消息内容
        String respContent = "\uD83E\uDD17欢迎关注Ai图灵程序员\n" +
                "\uD83D\uDD17项目开源地址：\n" +
                "https://github.com/a616567126/GPT-WEB-JAVA\n" +
                "\uD83D\uDD0Fdemo演示地址：\n" +
                url + "\n" +
                "\uD83D\uDCF1客户端功能：1-GPT3.5/4.0流式上下文对话，2-GPT画图，3-SD画图（联系作者开启功能），4-newBing流式对话，5-MJ画图\n\n" +
                "\uD83D\uDCE4微信公众号：1-扫码登录（暂未开放），2-MJ画图\n\n" +
                "\uD83D\uDC5B支付能力：1-易支付（微信，qq，支付宝），2-卡密支付\n\n" +
                "\uD83E\uDEAB注册能力：1-账号密码，2-邮箱，3-公众号\n\n" +
                "\uD83E\uDDD1\uD83C\uDFFB\u200D\uD83D\uDCBB管理端功能：1-用户管理，2-商品管理，3-订单管理，4-GPTKEY管理，5-卡密管理，6-邮箱管理，7-系统配置，8-操作日志\n\n" +
                "\uD83E\uDD77作者承接App，公众号，小程序，网站，物联网，定制软件，需要可添加作者微信：ssp941003\n\n" +
                "\uD83E\uDD77使用Mj等命令时记得开通或绑定现有账号，否则无法使用命令\n\n" +
                "\uD83C\uDFBC菜单\n\n" +
                "输 入 '查询' 即可剩余次数，账号创建时间\n\n" +
                "输 入 '绑定-手机号' 即可与当前微信用户绑定例如(绑定-13344445556)\n\n" +
                "输 入 '开通-手机号' 即可开通账号例如(开通-13344445556)同一个微信号只能开通一个账号默认使用次数5次，默认密码123456\n\n" +
                "输 入 '加群' 即可扫码添加作者微信,备注github\n\n" +
                "输 入 '/mj' 即可使用mj画图，例如'/mj 一只小狗'记得有空格,支持垫图，需先发送图片或直接使用图片地址进行垫图，注意图片地址中不要携带'#',多张空格分割 例如：/mj 一只小狗 # https://a.img.com https://b.img.com\n\n" +
                "输 入 '兑换-卡密' 即可兑换卡密例如'兑换-ABC1234'\n\n" +
//                "输 入 '迁移-手机号' 即可将当前微信用户迁移到手机号所在用户账号例如(迁移-13344444444)，并合并剩余次数，注意迁移账号后，微信关联用户则删除，请谨慎迁移，并核对手机号\n\n"+
                "输 入 '重置密码' 即可重置一个随机密码\n\n" +
                "输 入 '菜单' 进入菜单模式";
        // 调用parseXml方法解析请求消息
        WxMpXmlMessage message = WxMpXmlMessage.fromXml(request.getInputStream());
        // 发送方帐号
        String fromUser = message.getFromUser();
        // 开发者微信号
        String touser = message.getToUser();
        // 消息类型
        String msgType = message.getMsgType();
        Long tempUserId = null != message.getEventKey() ? Long.valueOf(message.getEventKey()) :0L;
        // 回复文本消息
        if (msgType.equals(CommonConst.REQ_MESSAGE_TYPE_EVENT)) {
            // 事件类型
            log.info("关注事件");
            User user = userService.getOne(fromUser,null);
            if (null != user) {
                if (user.getIsEvent() == 1 && tempUserId == 0) {
                    user.setIsEvent(0);
                } else {
                    user.setIsEvent(1);
                    wxUserLogin(user,request,tempUserId);
                    respContent =
                            "✅扫码成功，正在登录...\n\n"+
                                    respContent;
                }
                userService.saveOrUpdate(user);
            }else {
                user = new User();
                String password =  PasswordUtil.getRandomPassword();
                user.setName("用户"+System.currentTimeMillis());
                user.setPassword(SecureUtil.md5(password));
                user.setAvatar(CommonConst.AVATAR);
                user.setFromUserName(fromUser);
                user.setIsEvent(1);
                user.setRemainingTimes(sysConfig.getDefaultTimes());
                user.setType(1);
                userService.save(user);
                wxUserLogin(user,request,tempUserId);
                respContent =
                        "✅开通成功，正在登录...\n\n"+
                                respContent;
            }
        } else if (!msgType.equals(CommonConst.REQ_MESSAGE_TYPE_TEXT)) {
            if(msgType.equals(CommonConst.REQ_MESSAGE_TYPE_IMAGE)){
                respContent =
                        "图片地址：\n\n"
                        +message.getPicUrl()+"\n\n"
                        +"若想使用此图片垫图mj 使用以下命令：\n\n"
                        +"/mj 一只猫 # "+message.getPicUrl()+"\n\n"
                        +"注意图片地址中不要携带'#'，注意空格及顺序 /mj + 空格 + 咒语 + 空格 + '#' + 图片地址（多张用空格分割）";
            }else {
                respContent = "❗\uFE0F暂不支持该消息类型";
                log.info("其他消息");
            }
        } else {
            log.info("文本消息");
            String content = message.getContent();
            WxLog wxLog = new WxLog();
            wxLog.setContent(content);
            wxLog.setFromUserName(fromUser);
            wxLog.setCreateTime(LocalDateTime.now());
            wxLogService.save(wxLog);
            if (content.contains("绑定-")) {
                String[] split = content.split("-");
                if (split.length == 1) {
                    respContent = "❗\uFE0F输入内容格式不正确，请检查，或输入'菜单'查看格式'";
                } else {
                    if (!Validator.isMobile(split[1])) {
                        respContent = "❗\uFE0F请输入正确的手机号";
                    } else {
                        User user = userService.getOne(fromUser,null);
                        if (null != user) {
                            respContent = "❗\uFE0F当前微信已绑定账号：" + user.getMobile();
                        } else {
                            user = userService.getOne(null,split[1]);
                            if (null == user) {
                                respContent = "❗\uFE0F当前手机未注册，请输入'开通' 开通账号";
                            } else if (null != user.getFromUserName()) {
                                respContent = "❗\uFE0F当前手机号已绑定微信";
                            } else {
                                user.setFromUserName(fromUser);
                                user.setIsEvent(1);
                                userService.saveOrUpdate(user);
                                respContent = "✅账号绑定成功!" +
                                        "\n账号:" + user.getMobile() + "" +
                                        "\ndemo地址：" + url;
                            }
                        }
                    }
                }
            }
            if (content.contains("开通")) {
                String[] split = content.split("-");
                if (split.length == 1) {
                    respContent = "❗\uFE0F输入内容格式不正确，请检查，或输入'菜单'查看格式'";
                } else {
                    if (!Validator.isMobile(split[1])) {
                        respContent = "❗\uFE0F请输入正确的手机号";
                    } else {
                        User user = userService.getOne(fromUser,null);
                        if (null != user) {
                            respContent = "❗\uFE0F当前微信已绑定账号账号：" + user.getMobile();
                        } else {
                            user = userService.getOne(null,split[1]);
                            if (null != user) {
                                respContent = "❗\uFE0F当前手机号已被绑定";
                            } else {
                                user = new User();
                                String password =  PasswordUtil.getRandomPassword();
                                user.setName(split[1]);
                                user.setMobile(split[1]);
                                user.setPassword(SecureUtil.md5(password));
                                user.setFromUserName(fromUser);
                                user.setIsEvent(1);
                                user.setAvatar(CommonConst.AVATAR);
                                user.setRemainingTimes(sysConfig.getDefaultTimes());
                                user.setType(1);
                                userService.save(user);
                                respContent = "✅账号开通成功!" +
                                        "\n账号:" + user.getMobile() + "" +
                                        "\n密码:" +password+
                                        "\ndemo地址：" + url;
                            }
                        }
                    }
                }
            }
            else if (content.equals("查询")) {
                User user = userService.getOne(fromUser,null);
                if (null == user) {
                    respContent = "❗\uFE0F当微信用户前暂未绑定账号";
                } else {
                    respContent = "✅剩余次数：" + user.getRemainingTimes();
                }
            }
            else if (content.equals("菜单")) {
                respContent = "********菜单********\n\n" +
                        "输 入 '查询' 即可查询剩余次数，账号创建时间\n\n" +
                        "输 入 '绑定-手机号' 即可与当前微信用户绑定例如(绑定-13344445556)\n\n" +
                        "输 入 '开通-手机号' 即可开通账号例如(开通-13344445556)同一个微信号只能开通一个账号默认使用次数5次，默认密码123456\n\n" +
                        "输 入 '加群' 即可扫码添加作者微信,备注github\n\n" +
                        "输 入 '修改密码-新密码' 即可修改当前用户密码例如(修改密码-123456),长度不能超过16位\n\n" +
                        "输 入 '重置密码' 即可重置一个随机密码\n\n" +
                        "输 入 '/mj' 即可使用mj画图，例如'/mj 一只小狗'记得有空格,支持垫图，需先发送图片或直接使用图片地址进行垫图 例如：/mj 一只小狗 https://a.img.com\n\n" +
                        "输 入 '兑换-卡密' 即可兑换卡密例如'兑换-ABC1234'\n\n" +
//                        "输 入 '迁移-手机号' 即可将当前微信用户迁移到手机号所在用户账号例如(迁移-13344444444)，并合并剩余次数，注意迁移账号后，微信关联用户则删除，请谨慎迁移，并核对手机号\n\n"+
                        "输 入 '菜单' 进入菜单模式";
            }
            if (content.equals("加群")) {
                WxMpXmlOutMessage texts = WxMpXmlOutTextMessage
                        .IMAGE()
                        .toUser(fromUser)
                        .fromUser(touser)
                        .mediaId(CommonConst.MEDIA_ID)
                        .build();
                return texts.toXml();
            }
            if (content.startsWith("兑换")) {
                User user = userService.getOne(fromUser,null);
                if (null == user) {
                    respContent = "❗\uFE0F请先开通或绑定用户";
                }else {
                    String[] split = content.split("-");
                    if (split.length == 1) {
                        respContent = "❗\uFE0F输入内容格式不正确，请检查，或输入'菜单'查看格式'";
                    }else {
                        Order order = new Order();
                        List<CardPin> list = cardPinService.lambdaQuery().eq(CardPin::getCardPin, split[1]).list();
                        if(null == list || list.size() == 0 || list.get(0).getState() == 1){
                            respContent = "❗\uFE0F无效卡密";
                        }else {
                            order.setMsg("兑换成功");
                            order.setState(1);
                            order.setUserId(user.getId());
                            order.setPayNumber(1);
                            order.setPayType("卡密兑换");
                            order.setTradeNo(split[1]);
                            this.orderService.save(order);
                            userService.lambdaUpdate()
                                    .eq(User::getId,user.getId())
                                    .setSql("remaining_times = remaining_times +" + list.get(0).getNumber())
                                    .update();
                            cardPinService.lambdaUpdate()
                                    .eq(CardPin::getCardPin,split[1])
                                    .set(CardPin::getState,1)
                                    .set(CardPin::getUserId,user.getId())
                                    .update();
                            respContent = "✅兑换成功，此次兑换次数："+list.get(0).getNumber();
                        }
                    }

                }
            }
            if(content.startsWith("/mj")){
                User user = userService.getOne(fromUser,null);
                if (null == user) {
                    respContent = "❗\uFE0F请先开通或绑定用户";
                }else {
                    try {
                        boolean checkWxUser = checkService.checkWxUser(user.getId(), CommonConst.MJ_NUMBER);
                        if (checkWxUser) {

                            String prompt = content.indexOf("#") == -1 ? content.substring(4) : content.substring(4,content.indexOf("#"));
                            Task task = newTask(user.getId());
                            task.setAction(TaskAction.IMAGINE);
                            task.setPrompt(prompt);
                            String promptEn;
                            int paramStart = prompt.indexOf(" --");
                            if (paramStart > 0) {
                                promptEn = this.baiDuService.translateToEnglish(prompt.substring(0, paramStart)).trim() + prompt.substring(paramStart);
                            } else {
                                promptEn = this.baiDuService.translateToEnglish(prompt).trim();
                            }
                            task.setPromptEn(promptEn);
                            task.setFinalPrompt(promptEn);
                            task.setSubType(2);
                            List<String> imageUrlList = new ArrayList<String>();
                            if(content.contains("#")){
                                String[] contentValue = content.split("#");
                                if(contentValue.length == 2){
                                    String[] imgUrls = contentValue[1].split(" ");
                                    for (String imageUrl : imgUrls) {
                                        if(imageUrl.startsWith("http")){
                                            imageUrlList.add(imageUrl);
                                        }
                                    }
                                }
                            }
                            taskService.submitImagine(task,imageUrlList);
                            respContent = "✅咒语已提交，请等待出图";
                        } else {
                            respContent = "❗\uFE0F余额不足，请充值";
                        }
                    }catch (Exception e){
                        respContent = "❗\uFE0F命令有误，请重新输入";
                    }
                }

            }
            if(content.startsWith("/U") || content.startsWith("/V")){
                try {
                    User user = userService.getOne(fromUser, null);
                    boolean checkWxUser = checkService.checkWxUser(user.getId(), CommonConst.MJ_NUMBER);
                    String[] subContent = content.split(" ");
                    if (checkWxUser) {
                        respContent = "✅提交成功，请等待出图";
                        if (subContent.length < 3) {
                            respContent = "❗\uFE0F命令有误请检查";
                        } else {
                            Long taskId = Long.valueOf(subContent[2]);
                            Task task = taskStoreService.get(taskId);
                            if (null == task || task.getStatus() != TaskStatus.SUCCESS || !Arrays.asList(TaskAction.IMAGINE, TaskAction.VARIATION).contains(task.getAction())) {
                                respContent = "❗\uFE0F关联任务异常";
                            } else {
                                TaskAction taskAction = content.contains("U") ? TaskAction.UPSCALE : TaskAction.VARIATION;
                                Integer index = Integer.valueOf(subContent[1]);
                                String description = "/up "
                                        + task.getId()
                                        + " "
                                        + taskAction.name().charAt(0)
                                        + index;
                                TaskCondition condition = new TaskCondition().setDescription(description);
                                Task existTask = this.taskStoreService.findOne(condition);
                                if (null != existTask) {
                                    respContent = "❗\uFE0F任务已存在";
                                } else {
                                    Task mjTask = newTask(user.getId());
                                    mjTask.setAction(taskAction);
                                    mjTask.setPrompt(task.getPrompt());
                                    mjTask.setPromptEn(task.getPromptEn());
                                    mjTask.setFinalPrompt(task.getFinalPrompt());
                                    mjTask.setRelatedTaskId(task.getId());
                                    mjTask.setDescription(description);
                                    mjTask.setIndex(index);
                                    mjTask.setSubType(2);
                                    if (TaskAction.UPSCALE.equals(taskAction)) {
                                        this.taskService.submitUpscale(mjTask, task.getMessageId(), task.getMessageHash(), index,task.getFlags());
                                    } else if (TaskAction.VARIATION.equals(taskAction)) {
                                        this.taskService.submitVariation(mjTask, task.getMessageId(), task.getMessageHash(), index,task.getFlags());
                                    } else {
                                        respContent = "❗\uFE0F不支持的操作";
                                    }
                                }
                            }
                        }
                    } else {
                        respContent = "❗\uFE0F余额不足，请充值";
                    }
                }catch (Exception e) {
                    respContent = "❗\uFE0F命令有误，请重新输入";
                }
            }
            if (content.contains("迁移")) {
                User user = userService.getOne(fromUser,null);
                if (null == user) {
                    respContent = "❗\uFE0F微信暂未开通账号";
                }else {
                    if(null != user.getMobile()){
                        respContent = "❗\uFE0F请勿重复迁移";
                    }else {
                        String[] split = content.split("-");
                        if (split.length == 1) {
                            respContent = "❗\uFE0F输入内容格式不正确，请检查";
                        } else {
                            if (!Validator.isMobile(split[1])) {
                                respContent = "❗\uFE0F请输入正确的手机号";
                            } else {
                                User mobuleUser = userService.getOne(null,split[1]);
                                if (null == mobuleUser) {
                                    respContent = "❗\uFE0F手机号关联账号不存在";
                                }else {
                                    mobuleUser.setFromUserName(user.getFromUserName());
                                    mobuleUser.setRemainingTimes(mobuleUser.getRemainingTimes() + user.getRemainingTimes());
                                    userService.saveOrUpdate(mobuleUser);
                                    respContent = "✅账号迁移成功，只合并次数，消息，对话记录以手机号关联账号为准，微信原用户则删除";
                                    userService.removeById(user.getId());
                                }
                            }
                        }
                    }
                }
            }
            else if (content.contains("修改密码")) {
                User user = userService.getOne(fromUser,null);
                if (null == user) {
                    respContent = "❗\uFE0F请先开通或绑定账号";
                } else {
                    String[] split = content.split("-");
                    if (split.length == 1) {
                        respContent = "❗\uFE0F输入内容格式不正确，请检查，或输入'菜单'查看格式'";
                    } else {
                        if(split[1].length() > 16){
                            respContent = "❗\uFE0F请输入小于等于16位密码";
                        }else {
                            user.setPassword(SecureUtil.md5(split[1]));
                            respContent = "✅新密码为："+split[1];
                            userService.saveOrUpdate(user);
                        }
                    }
                }
            }
            else if (content.equals("重置密码")) {
                User user = userService.getOne(fromUser,null);
                if (null == user) {
                    respContent = "❗\uFE0F请先开通或绑定账号";
                } else {
                    String password =  PasswordUtil.getRandomPassword();
                    user.setPassword(SecureUtil.md5(password));
                    respContent = "✅重置密码完成，新密码："+password;
                    userService.saveOrUpdate(user);
                }
            }
        }
        WxMpXmlOutTextMessage texts = WxMpXmlOutTextMessage
                .TEXT()
                .toUser(fromUser)
                .fromUser(touser)
                .content(respContent)
                .build();
        return texts.toXml();
    }

    public void wxUserLogin(User user,HttpServletRequest request,Long tempUserId){
        SysConfig sysConfig = RedisUtil.getCacheObject(CommonConst.SYS_CONFIG);
        User nweUser = new User();
        nweUser.setId(user.getId());
        nweUser.setLastLoginTime(LocalDateTime.now());
        nweUser.setIpAddress(ServletUtil.getClientIP(request));
        userService.updateById(nweUser);
        if(!user.getAvatar().contains("http")){
            user.setAvatar(sysConfig.getImgReturnUrl() + user.getAvatar());
        }
        UserAuthRes loginResult = AuthController.createLoginResult(user);
        SseEmitterServer.sendMessage(tempUserId,loginResult);
    }
    private Task newTask(Long userId) {
        SysConfig sysConfig = RedisUtil.getCacheObject(CommonConst.SYS_CONFIG);
        Task task = new Task();
        task.setId(IDUtil.getNextId());
        task.setSubmitTime(System.currentTimeMillis());
        task.setNotifyHook(sysConfig.getApiUrl() + CommonConst.MJ_CALL_BACK_URL);
        task.setUserId(userId);
        return task;
    }
}
