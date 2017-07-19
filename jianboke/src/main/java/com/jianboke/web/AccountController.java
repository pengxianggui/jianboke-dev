package com.jianboke.web;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.jianboke.domain.AccountDefaultSetting;
import com.jianboke.domain.VerificationCode;
import com.jianboke.enumeration.HttpReturnCode;
import com.jianboke.mapper.UsersMapper;
import com.jianboke.model.*;
import com.jianboke.repository.AccountDefaultSettingRepository;
import com.jianboke.repository.UserRepository;
import com.jianboke.repository.VerificationCodeRepository;
import com.jianboke.security.SecurityUtils;
import com.jianboke.utils.FileUploadUtils;
import com.jianboke.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import com.jianboke.domain.User;
import com.jianboke.service.UserService;
import org.springframework.web.multipart.MultipartFile;

import javax.transaction.Transactional;

@RestController
@RequestMapping("/api")
public class AccountController {
	
    private final Logger log = LoggerFactory.getLogger(AccountController.class);
    
    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VerificationCodeRepository verificationCodeRepository;

    @Autowired
    private UsersMapper usersMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private FileUploadUtils fileUploadUtils;

    @Autowired
    private AccountDefaultSettingRepository accountDefaultSettingRepository;
    
    @RequestMapping(value = "/account", method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<User> getAccount() {
    	log.info("用户权限信息获取");
    	return Optional.ofNullable(userService.getUserWithAuthorities())
                .map(user -> new ResponseEntity<>(user, HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));
    }

    @RequestMapping(value = "/account/{id}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> getAccountName(@PathVariable Long id) {
        log.info("查询账户名");
        Map<String, String> map = new HashMap<>();
        String username = userService.findUsernameById(id);
        map.put("data", username);
        return map;
    }

    @RequestMapping(value = "/account/usernameUniqueValid", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ValidationResult> usernameUniqueValid(@RequestBody ValidationModel validation) {
        log.info("校验用户名的唯一性:{}" , validation.toString());
        return userRepository.findOneByUsername(validation.getValue())
                .map(user -> ResponseEntity.ok().body(ValidationResult.INVALID)) // user不为空表示唯一性验证失败
                .orElse(ResponseEntity.ok().body(ValidationResult.VALID)); // 否则唯一性验证成功
    }

    @RequestMapping(value = "/account/emailUniqueValid", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ValidationResult> emailUniqueValid(@RequestBody ValidationModel validation) {
        log.info("校验注册邮箱的唯一性:{}", validation.toString());
        return userRepository.findOneByEmail(validation.getValue())
                .map(user -> ResponseEntity.ok().body(ValidationResult.INVALID)) // user不为空表示唯一性验证失败
                .orElse(ResponseEntity.ok().body(ValidationResult.VALID)); // 否则唯一性验证成功
    }

    @RequestMapping(value = "/account/sendEmailValidCode", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ValidationResult> sendEmailValidCode(@RequestBody EmailValidCodeModel model) {
        log.info("发送邮件验证码 :{}", model.getEmail());
        String code = StringUtils.randomNumberStr(6);
        if (userService.sendEmailCodeForVaild(model.getEmail(), code)) {
            // TODO 将code存储起来
            return verificationCodeRepository.findOneByEmail(model.getEmail())
            .map(vc -> {
                vc.setCode(code);
                verificationCodeRepository.saveAndFlush(vc);
                return ResponseEntity.ok().body(ValidationResult.VALID);
            }).orElseGet(() -> {
                verificationCodeRepository.saveAndFlush(new VerificationCode(model.getEmail(), code));
                return ResponseEntity.ok().body(ValidationResult.VALID);
            });
        }
        return ResponseEntity.ok().body(ValidationResult.INVALID);
    }

    @RequestMapping(value = "/account/register", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RequestResult> register(@RequestBody UsersModel model) {
        log.info("Rest Request to register a new user :{}", model);
        // 校验username和email都在数据库中不存在
        if (userRepository.findOneByEmail(model.getEmail()).isPresent()
                || userRepository.findOneByUsername(model.getUsername()).isPresent()) {
            return ResponseEntity.ok().body(RequestResult.create(HttpReturnCode.JBK_ACCOUNT_IS_EXIST));
        }
        return verificationCodeRepository.findOneByEmail(model.getEmail())
            .filter(verificationCode -> userService.verificationCodeValid(model, verificationCode))
            .map(verificationCode -> {
                User user = usersMapper.modelToEntity(model);
                user.setPassword(passwordEncoder.encode(model.getPassword())); // 加密
                userRepository.saveAndFlush(user);
                User u = user;
                u.setPassword(model.getPassword());
                return ResponseEntity.ok()
                    .body(RequestResult.create(HttpReturnCode.JBK_SUCCESS, u));
            })
            .orElse(ResponseEntity.ok().body(RequestResult.create(HttpReturnCode.JBK_VERIFICATION_CODE_WRONG)));
    }

    /**
     * 上传用户头像
     * @param file
     * @return
     */
    @Transactional
    @RequestMapping(value = "/account/avator", method = RequestMethod.POST)
    public ResponseEntity<RequestResult> uploadAvator(@RequestParam("file") MultipartFile file) {
        log.info("rest request to upload avator for the user:{}", SecurityUtils.getCurrentUsername());
        ResponseEntity<RequestResult> result =  fileUploadUtils.uploadAvator(file);
        if (result.getBody().getCode().equals("0000")) { // 保存路径
            userService.updateAvatarPath((String) result.getBody().getData());
        }
        return result;
    }

    @RequestMapping(value = "/account/updateUsername/{username}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RequestResult> updateUsername(@PathVariable String username) {
        log.info("rest request to update the username for :{}", SecurityUtils.getCurrentUsername());
        if(userService.updateUsername(username) != null) {
            return ResponseEntity.ok().body(RequestResult.create(HttpReturnCode.JBK_SUCCESS));
        }
        return ResponseEntity.ok().body(RequestResult.create(HttpReturnCode.JBK_ERROR));
    }

    @RequestMapping(value = "/account/getShowDarkTheme", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RequestResult> getShowDarkTheme() {
        log.info("REST resquest to get showDarkTheme or not...");
        User user = userService.getUserWithAuthorities();
        return accountDefaultSettingRepository.findOneByUserId(user.getId())
                .map(setting -> {
                    System.out.println(setting.toString());
                    return ResponseEntity.ok().body(RequestResult.create(HttpReturnCode.JBK_SUCCESS, setting.isDarkTheme()));
                })
                .orElseGet(null);
    }

    @RequestMapping(value = "/account/saveShowDarkTheme/{showDarkTheme}", method = RequestMethod.GET)
    public void saveShowDarkTheme(@PathVariable Boolean showDarkTheme) {
        log.info("REST request to save showDarkTheme:{}", showDarkTheme);
        User user = userService.getUserWithAuthorities();
        accountDefaultSettingRepository.findOneByUserId(user.getId())
            .map(setting -> {
            setting.setDarkTheme(showDarkTheme);
            accountDefaultSettingRepository.saveAndFlush(setting);
            return setting;
        });
    }
}
