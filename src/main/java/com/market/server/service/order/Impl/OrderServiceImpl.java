package com.market.server.service.order.Impl;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.market.server.dao.OrderLogDao;
import com.market.server.dao.ProductDao;
import com.market.server.dto.Search;
import com.market.server.dto.order.OrderDTO;
import com.market.server.dto.order.OrderDetailDTO;
import com.market.server.dto.product.ProductDetailDTO;
import com.market.server.dto.push.PushMessage;
import com.market.server.error.exception.TotalPriceMismatchException;
import com.market.server.mapper.order.OrderMapper;
import com.market.server.service.order.OrderService;
import com.market.server.service.product.Impl.ProductServiceImpl;
import com.market.server.service.push.PushServiceImpl;
import com.market.server.service.user.Impl.UserServiceImpl;
import com.market.server.utils.RedisKeyFactory;

import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
public class OrderServiceImpl implements OrderService{
	
	@Autowired
	private OrderMapper orderMapper;

	@Autowired
	private ProductServiceImpl productService;
	
	@Autowired
	private OrderLogDao orderLogDao;
	
	@Autowired
	private ProductDao productDao; 
	
	@Autowired
	private PushServiceImpl pushService;
	
	@Autowired
	private UserServiceImpl userService;
	
	/**
	 * 상품을 주문한다.
	 */
	@Override
	@Transactional(rollbackFor = RuntimeException.class)
	public void doOrder(OrderDTO orderDTO) {
		
		SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmss");
		Date time = new Date();
		// 주문코드 조합 : 주문코드 + 로그인번호 + 현재시간
		String orderCd = "OD" + orderDTO.getLoginNo() + format.format(time);
		orderDTO.setOrderCd(orderCd);
		
		Search search = new Search();
		search.add("itemCd", orderDTO.getItemCd());
		ProductDetailDTO productDTO = productService.productDetail(search);
		
		// 총결제금액 확인
		int orderCnt       = orderDTO.getOrderCnt();                        // 주문수량
		long orderPrice    = productDTO.getProductDTO().getItemPrice();     // 주문금액
		long deliveryPrice = productDTO.getProductDTO().getDeliveryPrice(); // 배송금액
		long discountPrice = orderDTO.getDiscountPrice();                   // 할인금액
		long totalPrice    = (orderCnt * orderPrice) + deliveryPrice - discountPrice; // server total price
		
		if(totalPrice != orderDTO.getTotalPrice()) {
			log.error("Total Price Mismatch! client price : {}, server price : {},",
			          orderDTO.getTotalPrice(), totalPrice);
			throw new TotalPriceMismatchException("Total Price Mismatch!");
		}
		
		int result = orderMapper.doOrder(orderDTO);
		
		if(result != 1) {
			log.error("Insert ERROR! {}", orderDTO);
			throw new RuntimeException("Insert ERROR! 주문정보를 확인해주세요.\n" + "orderDTO : " + orderDTO);
		}
	}

	@Override
	public List<OrderDetailDTO> getOrderList(int loginNo) {
		return orderMapper.getOrderList(loginNo);
	}

	/**
	 * 주문상품의 주문상태코드를 변경한다.
	 */
	@Override
	@Transactional(rollbackFor = RuntimeException.class)
	public void updateOrderStatus(OrderDTO orderDTO) {
		
		String orderCd = orderDTO.getOrderCd();
		String orderStatusCd = orderDTO.getOrderStatusCd();
		
		int result = orderMapper.updateOrderStatus( orderCd, orderStatusCd);
		
		if(result != 1) {
			log.error("Update ERROR! {}", orderCd);
			throw new RuntimeException("Update ERROR! 주문번호를 확인해주세요.\n" + "orderCd : " + orderCd);
		}else {
			//현재시간 계산
			SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			Date time = new Date();
			
			orderLogDao.addOrder(new OrderDTO(orderCd, orderStatusCd, format.format(time)));
		}
		
		// 배송완료 시 주문수량 count 저장, OSC007 - 배송완료
		if("OSC007".equals(orderStatusCd)) {
			int orderCnt = productDao.getProductCntInfo(RedisKeyFactory.ORDER_CNT_KEY, orderDTO.getItemCd());
			productDao.addProductCntInfo(RedisKeyFactory.ORDER_CNT_KEY, orderDTO.getItemCd(), orderCnt + orderDTO.getOrderCnt());
		}
		
		// 환불완료 시 주문수량 count 변경, OSC009 - 환불완료
		if("OSC009".equals(orderStatusCd)) {
			int orderCnt = productDao.getProductCntInfo(RedisKeyFactory.ORDER_CNT_KEY, orderDTO.getItemCd());
			productDao.addProductCntInfo(RedisKeyFactory.ORDER_CNT_KEY, orderDTO.getItemCd(), orderCnt - orderDTO.getOrderCnt());
		}
		
		//사용자 아이디 get
		String userId = userService.findById(orderDTO.getLoginNo());
		
		//푸시메시지 전송
		sendPushMessage(userId, orderStatusCd);
	}

	@Override
	public void sendPushMessage(String userId, String orderStatusCd) {
		PushMessage messageInfo = new PushMessage();
		
		switch (orderStatusCd) {
		case "OSC001": // 상품접수
			messageInfo = PushMessage.ORDER_STATUS_ACCEPT;
			break;
		case "OSC003": // 잡화처리
			messageInfo = PushMessage.ORDER_STATUS_CORRECT;
			break;
		case "OSC005": // 배송출발
			messageInfo = PushMessage.ORDER_STATUS_START;
			break;
		case "OSC007": // 배송완료
			messageInfo = PushMessage.ORDER_STATUS_COMPLETE;
			break;
		case "OSC008": //환불
			messageInfo = PushMessage.ORDER_STATUS_REFUND;
			break;
		case "OSC009": //환불완료
			messageInfo = PushMessage.ORDER_STATUS_REFUND_COMPLETE;
			break;
		}
		
		pushService.sendMessageToUser(messageInfo, userId);
		
	}
}
