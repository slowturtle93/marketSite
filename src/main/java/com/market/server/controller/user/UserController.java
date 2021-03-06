package com.market.server.controller.user;

import javax.servlet.http.HttpSession;
import javax.validation.constraints.NotNull;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.market.server.aop.LoginCheck;
import com.market.server.aop.LoginCheck.UserType;
import com.market.server.dto.user.UserDTO;
import com.market.server.service.push.PushServiceImpl;
import com.market.server.service.user.Impl.UserServiceImpl;
import com.market.server.utils.SessionUtil;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;


/**
 * Java Logging
 * <p>
 * Log4j 속도와 유연성을 고려하여 디자인되어있어 속도에 최적화 되어있다. 멀티 스레드 환경에서 안전하다.
 * </p>
 * <p>
 * SLF4J 로깅에 대한 추상 레이어를 제공한다. 로깅의 인터페이스 역할을 한다. 공통 인터페이스 역할을 하기 때문에 구현체의 종류와 상관 없이 일관된 로깅코드를 작성할 수
 * 있다. slf4j만으로는 로깅을 실행할 수 없어서 commons logging, log4j, logback 등의 로깅 구현체를 적용해야 한다.
 * </p>
 * Logback Log4j를 토대로 만든 새로운 Logging 라이브러리이다. SLF4J를 통해 다른 로깅 프레임워크를 logback으로 통합할 수 있다. Log4j보다 10배
 * 높은 속도 퍼포먼스를 보이도록 설계되어있으며 메모리 효율을 개선하였다. 설정 파일 변경시 서버 재가동 없이도 자동 변경 갱신이 이루어진다. 로깅 I/O시 Failure에 대한
 * 복구를 서버 중지 없이도 지원하고있다. Logback사용을 위해서는 SLF4J와 함께 사용해야 한다.(Logback은 SLF4J의 구현체이다)
 * <p>
 * Log4j2 멀티 스레드 환경에서 Logback보다 10배 높은 성능 퍼포먼스를 기대할 수 있다. Log4j, Logback에 존재하는 동기화 이슈 문제를 해결하였다. 멀티
 * 스레드 환경 로깅이 필요하다면 Log4j2를 사용하는 것이 성능면에서 유리하다. 사용자 정의 로그레벨과 람다 표현식을 지원한다. Log4j2 자체적으로 직접 사용할 수는
 * 있지만 일반적으로는 SLF4J와 함께 사용한다.
 * </p>
 * 
 * @Log @Slf4j @Log4j2 등 어노테이션 적용시 자동으로 log 필드를 만들고 해당 클래스의 이름으로 로거 객체를 생성하여 할당한다.
 * 
 * @author haksong
 *
 */
@RestController
@RequestMapping("/user/")
@Log4j2
public class UserController {
	
	@Autowired
	private UserServiceImpl userService;
	
//	@Autowired
	private PushServiceImpl pushService;
	
	/**
	 * 회원이 myPage 클릭 시 사용자 정보 반환
	 * 
	 * @param session
	 * @return
	 */
	@GetMapping("myInfo")
	@LoginCheck(type = UserType.USER)
	public UserInfoResponse userInfo(HttpSession session) {
		String loginId  = SessionUtil.getLoginUserId(session);
		UserDTO userDTO = userService.getUserInfo(loginId);
		return new UserInfoResponse(userDTO);
	}

	/**
	 * 회원가입 시 ID 중복체크 여부 확인.
	 * 
	 * 회원가입 시 아이디의 중복체크를 진행한다. 아이디 중복체크는 회원가입 아이디 입력 후, 회원가입 요청시 두번 진행한다. 아이디 중복체크를 한 후 회원가입 버튼을 누를 때 까지
	 * 동일한 아이디로 누군가 가입한다면 PK Error가 발생되고 실제로 회원가입이 진행되지 않을 수 있기 때문에 회원가입을 눌렀을 때 한번 더 실행하는 것이 좋다.
	 *  
	 * @param loginId
	 * @return
	 */
	@GetMapping("duplicated/{id}")
	public boolean idCheck(@PathVariable @NotNull String id){
		return userService.isDuplicatedId(id);
	}
	
	/**
	 * 유저가 입력한 정보로 회원가입을 진행한다. 보낸 값들 중 NULL값이 있으면 "NULL_ARGUMENT" 를 리턴한다. 회원가입 요청을 보내기 전 먼저 ID 중복체크를
	 * 진행한다. ID 중복시 403 상태코드를 반환한다. 회원가입 성공시 201 상태코드를 반환한다.
	 * 
	 * ResponseEntity는 HTTP 요청(Request) 또는 응답(Response)에 해당하는 HttpHeader와 HttpBody를 포함하는 클래스
	 * 
	 * @param userDTO
	 * @return
	 */
	@PostMapping("signUp")
	@ResponseStatus(HttpStatus.CREATED)
	public ResponseEntity<UserResultResponse> register(@RequestBody @NotNull UserDTO userDTO){
		ResponseEntity<UserResultResponse> responseEntity = null; // 반환값
		UserResultResponse userResultResponse;                    // 상태값
		
		// 사용자 정보 필수값 체크
	    if(UserDTO.hasNullDataBeforeSignup(userDTO)) {
	    	throw new NullPointerException("회원가입 시 필수 입력값을 모두 입력해야 합니다.");
	    }
	    
	    // 사용자 등록
	    int result = userService.insert(userDTO);
	    
	    if(result == 1) { // 성공인 경우
	    	userResultResponse = UserResultResponse.SUCCESS;
		    responseEntity     = new ResponseEntity<UserResultResponse>(userResultResponse, HttpStatus.OK);
	    }else { // 실패인 경우
	    	log.error("insertUser ERROR! {}", userDTO);
	    	userResultResponse = UserResultResponse.FAIL;
		    responseEntity     = new ResponseEntity<UserResultResponse>(userResultResponse, HttpStatus.UNAUTHORIZED);
	    }
	    
	    return responseEntity;
	}
	
	/**
	 * 회원 로그인을 진행합니다. login 요청 시 loginId, loginPw 가 NULL 일 경우 NullPointerException을 throw 합니다.
	 * 
	 * @param loginRequest
	 * @param session
	 * @return
	 */
	@PostMapping("login")
	public ResponseEntity<LoginResponse> login(@RequestBody @NotNull UserLoginRequest loginRequest, HttpSession session){
		ResponseEntity<LoginResponse> responseEntity = null; // 반환값
		LoginResponse loginResponse;                         // 상태값
		
		//사용자 정보 조회
		UserDTO userDTO = userService.login(loginRequest.getLoginId(), loginRequest.getLoginPw());

		if(userDTO == null) { // ID, PW에 맞는 정보가 없을 때
			 loginResponse  = LoginResponse.FAIL;
			 responseEntity = new ResponseEntity<LoginResponse>(loginResponse, HttpStatus.UNAUTHORIZED);
		}else if(UserDTO.Status.DEFAULT.equals(userDTO.getStatus())) { // 로그인 성공 시 세션에 ID 저장
			SessionUtil.setLoginUserInfo(session, userDTO.getLoginNo(), loginRequest.getLoginId());
			loginResponse  = LoginResponse.success(userDTO);
			responseEntity = new ResponseEntity<LoginResponse>(loginResponse, HttpStatus.OK);
		}else { // 예상하지 못한 오류일 경우
			log.error("login Error " + responseEntity);
			throw new RuntimeException("login Error!");
		}
			
		return responseEntity;
	}
	
	/**
	 * 회원 로그아웃
	 * 
	 * @param session
	 */
	@GetMapping("logout")
	public void logout(HttpSession session) {
		SessionUtil.logoutUserInfo(session);
	}
	
	/**
	 * 회원의 비밀번호 정보 변경
	 * 
	 * @param passwordRequest
	 * @param session
	 */
	@PatchMapping("password")
	@LoginCheck(type = UserType.USER)
	public ResponseEntity<UserResultResponse> UserUpdatePassword(@RequestBody @NotNull UpdatePasswordRequest passwordRequest,
			                                                     HttpSession session) {
		ResponseEntity<UserResultResponse> responseEntity = null; // 반환값
		UserResultResponse userResultResponse;                    // 상태값
		String passwordBeforeChange = (String) passwordRequest.getPasswordBeforeChange(); // 이전 비밀번호
	    String passwordAfterChange  = (String) passwordRequest.getPasswordAfterChange();  // 변경할 비밀번호
	    String loginId = SessionUtil.getLoginUserId(session); // 세션에 저장된 사용자 아이디
	    int loginNo    = SessionUtil.getLoginUserNo(session); // 세션에 저장된 사용자 번호
	    
	    // 유효성 검사
	    if(passwordAfterChange == null || passwordBeforeChange == null) { 
	    	throw new NullPointerException("패스워드를 입력해주세요.");
	    }
	    
	    // 사용자 비밀번호 변경
	    int result = userService.updatePassword(loginNo, loginId, passwordBeforeChange, passwordAfterChange);
	    
	    if(result == 1) { //성공인 경우
	    	userResultResponse = UserResultResponse.SUCCESS;
		    responseEntity     = new ResponseEntity<UserResultResponse>(userResultResponse, HttpStatus.OK);
	    }else { //실패인 경우
	    	log.error("update Password Error id : {}, pw : {}", loginId, passwordAfterChange);
	    	userResultResponse = UserResultResponse.FAIL;
		    responseEntity     = new ResponseEntity<UserResultResponse>(userResultResponse, HttpStatus.UNAUTHORIZED);
	    }
	    
	    return responseEntity;
	}
	
	/**
	 * 회원 정보 필수 값 NULL체크 후 회원 정보 업데이트 진행
	 * 
	 * @param updateAddressRequest
	 * @param session
	 * @return
	 */
	@PatchMapping("update")
	@LoginCheck(type = UserType.USER)
	public ResponseEntity<UpdateUserResponse> updataAddress(@RequestBody UserDTO userDTO, HttpSession session){
		ResponseEntity<UpdateUserResponse> responseEntity = null; // 반환값
		userDTO.setLoginNo(SessionUtil.getLoginUserNo(session));  // 사용자번호 Set
		
		if(userDTO.getRoadFullAddr() == null || userDTO.getJibunAddr() == null || userDTO.getZipNo() == null) {
			//주소 정보 NULL인 경우
			responseEntity = new ResponseEntity<UpdateUserResponse>(UpdateUserResponse.EMPTY_ADDRESS, HttpStatus.BAD_REQUEST);
		}else if(userDTO.getAddrDetail() == null) {
			//주소 상세 정보 NULL인 경우
			responseEntity = new ResponseEntity<UpdateUserResponse>(UpdateUserResponse.EMPTY_ADDRESS_DETAIL, HttpStatus.BAD_REQUEST);
		}else if(userDTO.getHpNum() == null) {
			//핸드폰 정보 NULL인 경우
			responseEntity = new ResponseEntity<UpdateUserResponse>(UpdateUserResponse.EMPTY_HPNUMBER, HttpStatus.BAD_REQUEST);
		}else if(userDTO.getEmail() == null) {
			//이메일 정보 NULL인 경우
			responseEntity = new ResponseEntity<UpdateUserResponse>(UpdateUserResponse.EMPTY_EMAIL, HttpStatus.BAD_REQUEST);
		}else if(userDTO.getUserNm() == null) {
			//사용자명 정보 NULL인 경우
			responseEntity = new ResponseEntity<UpdateUserResponse>(UpdateUserResponse.EMPTY_USERNM, HttpStatus.BAD_REQUEST);
		}else {
			//수정 정보 이상 없을 시
			userService.update(userDTO);
			responseEntity = new ResponseEntity<UpdateUserResponse>(UpdateUserResponse.SUCCESS, HttpStatus.OK);
		}
		
		return responseEntity;
	}
	
	
	/**
	 * 회원 정보 삭제하기 전 비밀번호 인증 일치여부 확인 후 회원 정보 삭제 진행
	 * 
	 * @param userDelete
	 * @param session
	 * @return
	 */
	@DeleteMapping("delete")
	@LoginCheck(type = UserType.USER)
	public ResponseEntity<UserResultResponse> deleteUser(@RequestBody @NotNull UserDelete userDelete, HttpSession session){
		ResponseEntity<UserResultResponse> responseEntity = null; // 반환값
		UserResultResponse userResultResponse;                    // 상태값
		
		String loginId = SessionUtil.getLoginUserId(session); // 로그인 아이디
		int loginNo    = SessionUtil.getLoginUserNo(session); // 로그인 번호
		
		//삭제 전 비밀번호 확인
		userService.passwordMatch(loginId, userDelete.getLoginPw());
		// 사용자 정보 삭제
		int result = userService.delete(loginNo);
		
		if(result == 1) { //성공인 경우
			logout(session);
			userResultResponse = UserResultResponse.SUCCESS;
		    responseEntity     = new ResponseEntity<UserResultResponse>(userResultResponse, HttpStatus.OK);
		}else { //실패인 경우
			log.error("delete User Error! id : {}", SessionUtil.getLoginUserId(session));
			userResultResponse = UserResultResponse.FAIL;
		    responseEntity     = new ResponseEntity<UserResultResponse>(userResultResponse, HttpStatus.NOT_FOUND);
		}
	    return responseEntity;
	}
	
	/**
	 * 사용자 token 정보 저장
	 * 
	 * @param session
	 * @param token
	 */
	@PostMapping("token")
	@LoginCheck(type = UserType.USER)
	public void addToken(HttpSession session, String token) {
		String userId = SessionUtil.getLoginUserId(session);
		pushService.addUserToken(userId, token);
	}
	
	/*======================= response 객체 ======================= */
	
	 @Getter                   
	 @AllArgsConstructor       // 필드값을 모두 포함한 생성자를 자동 생성해준다.
	 @RequiredArgsConstructor  // 생성자를 자동 생성하지만, 필드명 위에 @nonNull로 표기된 경우만 생성자의 매개변수로 받는다.
	 private static class LoginResponse {
	   enum LoginStatus {
	     SUCCESS, FAIL, DELETED
	   }

	   @NonNull //null을 허용하지 하지 않음
	   private LoginStatus result;
	   private UserDTO userDTO;

	   // success의 경우 memberInfo의 값을 set해줘야 하기 때문에 new 하도록 해준다.
	   private static final LoginResponse FAIL = new LoginResponse(LoginStatus.FAIL);
	   private static LoginResponse success(UserDTO userDTO) {
	     return new LoginResponse(LoginStatus.SUCCESS, userDTO);
	   }
	 }
	
     @Getter
     @AllArgsConstructor
     private static class UserInfoResponse {
         private UserDTO userDTO;
     }
     
     @Getter
     private static class UserResultResponse{
    	 enum UserResultStatus{
    		 SUCCESS, FAIL
    	 }
    	 
    	 @NonNull
    	 UserResultStatus message;
    	 
    	 private static final UserResultResponse SUCCESS = new UserResultResponse(UserResultStatus.SUCCESS);
    	 private static final UserResultResponse FAIL    = new UserResultResponse(UserResultStatus.FAIL);
    	 
    	 public UserResultResponse(UserResultStatus message) {
   	      this.message = message;
   	    }
     }
	
	  @Getter
	  private static class UpdateUserResponse {
	    enum UpdateStatus {
	    	SUCCESS, EMPTY_ADDRESS, EMPTY_ADDRESS_DETAIL, EMPTY_HPNUMBER, EMPTY_EMAIL, EMPTY_USERNM 
	    }

	    @NonNull
	    private UpdateStatus message;

	    private static final UpdateUserResponse SUCCESS              = new UpdateUserResponse(UpdateStatus.SUCCESS);
	    private static final UpdateUserResponse EMPTY_ADDRESS        = new UpdateUserResponse(UpdateStatus.EMPTY_ADDRESS);
	    private static final UpdateUserResponse EMPTY_ADDRESS_DETAIL = new UpdateUserResponse(UpdateStatus.EMPTY_ADDRESS_DETAIL);
	    private static final UpdateUserResponse EMPTY_HPNUMBER       = new UpdateUserResponse(UpdateStatus.EMPTY_HPNUMBER);
	    private static final UpdateUserResponse EMPTY_EMAIL          = new UpdateUserResponse(UpdateStatus.EMPTY_EMAIL);
	    private static final UpdateUserResponse EMPTY_USERNM         = new UpdateUserResponse(UpdateStatus.EMPTY_USERNM);

	    public UpdateUserResponse(UpdateStatus message) {
	      this.message = message;
	    }
	  }
	
	/*======================= request 객체 ======================= */
	
	  @Getter
	  @Setter
	  private static class UserLoginRequest {
	    @NonNull
	    private String loginId;
	    @NonNull
	    private String loginPw;
	  }
	  
	  @Getter
	  @Setter
	  private static class UpdatePasswordRequest {
	    @NonNull
	    private String passwordBeforeChange;
	    @NonNull
	    private String passwordAfterChange;
	  }
	  
	  @Setter
	  @Getter
	  private static class UserDelete {
	      @NonNull
	      private String loginPw;
	  }
}
