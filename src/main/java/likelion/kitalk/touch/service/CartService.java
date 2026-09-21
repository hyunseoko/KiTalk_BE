package likelion.kitalk.touch.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import likelion.kitalk.global.exception.CustomException;
import likelion.kitalk.touch.dto.CartData;
import likelion.kitalk.touch.dto.CartEntry;
import likelion.kitalk.touch.dto.request.CartAddRequest;
import likelion.kitalk.touch.dto.request.CartRemoveRequest;
import likelion.kitalk.touch.dto.request.CartUpdateRequest;
import likelion.kitalk.touch.dto.request.PackagingRequest;
import likelion.kitalk.touch.dto.response.CartResponse;
import likelion.kitalk.touch.exception.CartErrorCode;
import likelion.kitalk.touch.util.CartUtils;
import likelion.kitalk.touch.validator.CartValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {
  private static final String CART_KEY_PREFIX = "touch_cart:";
  private static final String PACKAGING_KEY_PREFIX = "touch_packaging:";
  private static final long CART_EXPIRE_HOURS = 2;

  private final RedisTemplate<String, String> redisTemplate;
  private final ObjectMapper objectMapper;
  private final CartValidator cartValidator;
  private final CartUtils cartUtils;

  public Map<String, Object> addToCart(String sessionId, CartAddRequest request) {
    cartValidator.validateAddRequest(sessionId, request);
    try {
      CartData cart = getCartData(sessionId);
      CartEntry existing = cart.getItems().stream()
          .filter(item -> item.getMenuId().equals(request.getMenuId()))
          .findFirst().orElse(null);
      if (existing == null) {
        cart.getItems().add(new CartEntry(request.getMenuId(), request.getQuantity()));
      } else {
        existing.setQuantity(Math.addExact(existing.getQuantity(), request.getQuantity()));
      }
      saveCartData(sessionId, cart);
      return response("장바구니에 담겼습니다", cart, sessionId);
    } catch (CustomException e) {
      throw e;
    } catch (Exception e) {
      log.error("장바구니 담기 실패", e);
      throw new CustomException(CartErrorCode.CART_UPDATE_FAILED);
    }
  }

  public Map<String, Object> updateCart(String sessionId, CartUpdateRequest request) {
    cartValidator.validateUpdateRequest(sessionId, request);
    try {
      CartData cart = getCartData(sessionId);
      Map<Long, Integer> requested = new HashMap<>();
      for (CartUpdateRequest.CartUpdateItem item : request.getOrders()) {
        requested.put(item.getMenu_id(), item.getQuantity());
      }
      int before = cart.getItems().size();
      cart.getItems().removeIf(item -> !requested.containsKey(item.getMenuId())
          || requested.get(item.getMenuId()) == 0);
      int removed = before - cart.getItems().size();
      int added = 0;
      int updated = 0;
      for (Map.Entry<Long, Integer> item : requested.entrySet()) {
        if (item.getValue() == 0) {
          continue;
        }
        CartEntry existing = cart.getItems().stream()
            .filter(entry -> entry.getMenuId().equals(item.getKey()))
            .findFirst().orElse(null);
        if (existing == null) {
          cart.getItems().add(new CartEntry(item.getKey(), item.getValue()));
          added++;
        } else if (!existing.getQuantity().equals(item.getValue())) {
          existing.setQuantity(item.getValue());
          updated++;
        }
      }
      saveCartData(sessionId, cart);
      return response(String.format("장바구니가 업데이트되었습니다 (추가: %d, 변경: %d, 제거: %d)",
          added, updated, removed), cart, sessionId);
    } catch (CustomException e) {
      throw e;
    } catch (Exception e) {
      log.error("장바구니 업데이트 실패", e);
      throw new CustomException(CartErrorCode.CART_UPDATE_FAILED);
    }
  }

  public Map<String, Object> removeMenuItem(String sessionId, CartRemoveRequest request) {
    cartValidator.validateRemoveRequest(sessionId, request);
    try {
      CartData cart = getCartData(sessionId);
      boolean removed = cart.getItems().removeIf(
          item -> item.getMenuId().equals(request.getMenuId()));
      if (!removed) {
        throw new CustomException(CartErrorCode.CART_ITEM_NOT_FOUND);
      }
      saveCartData(sessionId, cart);
      return response("메뉴가 삭제되었습니다", cart, sessionId);
    } catch (CustomException e) {
      throw e;
    } catch (Exception e) {
      log.error("장바구니 메뉴 삭제 실패", e);
      throw new CustomException(CartErrorCode.CART_UPDATE_FAILED);
    }
  }

  public Map<String, Object> clearCart(String sessionId) {
    cartValidator.validateSessionOnly(sessionId);
    try {
      redisTemplate.delete(CART_KEY_PREFIX + sessionId);
      return response("장바구니가 비워졌습니다", emptyCart(), sessionId);
    } catch (CustomException e) {
      throw e;
    } catch (Exception e) {
      log.error("장바구니 비우기 실패", e);
      throw new CustomException(CartErrorCode.CART_CLEAR_FAILED);
    }
  }

  public Map<String, Object> getCart(String sessionId) {
    cartValidator.validateSessionOnly(sessionId);
    try {
      return response("장바구니 조회 성공", getCartData(sessionId), sessionId);
    } catch (CustomException e) {
      throw e;
    } catch (Exception e) {
      log.error("장바구니 조회 실패", e);
      throw new CustomException(CartErrorCode.CART_FETCH_FAILED);
    }
  }

  public Map<String, Object> setPackagingType(String sessionId, PackagingRequest request) {
    cartValidator.validatePackagingRequest(sessionId, request);
    try {
      Map<String, Object> packaging = new HashMap<>();
      packaging.put("packagingType", request.getPackagingType());
      packaging.put("updatedAt", now());
      redisTemplate.opsForValue().set(PACKAGING_KEY_PREFIX + sessionId,
          objectMapper.writeValueAsString(packaging), CART_EXPIRE_HOURS, TimeUnit.HOURS);
      return cartUtils.convertToMap(cartUtils.createPackagingResponse(
          "포장 방식이 설정되었습니다", sessionId, request.getPackagingType()));
    } catch (CustomException e) {
      throw e;
    } catch (Exception e) {
      log.error("포장 방식 설정 실패", e);
      throw new CustomException(CartErrorCode.PACKAGING_UPDATE_FAILED);
    }
  }

  private Map<String, Object> response(String message, CartData cart, String sessionId) {
    // PhoneService still consumes the shared Redis JSON as maps.
    List<Map<String, Object>> items = new ArrayList<>();
    for (CartEntry entry : cart.getItems()) {
      Map<String, Object> item = new HashMap<>();
      item.put("menuId", entry.getMenuId());
      item.put("quantity", entry.getQuantity());
      items.add(item);
    }
    var orders = cartUtils.convertToCartItemDetails(items);
    CartResponse result = CartResponse.builder()
        .message(message)
        .orders(orders)
        .total_items(orders.size())
        .total_price(cartUtils.calculateTotalPrice(items))
        .packaging(getPackagingType(sessionId))
        .session_id(sessionId)
        .build();
    return cartUtils.convertToMap(result);
  }

  private String getPackagingType(String sessionId) {
    String json = redisTemplate.opsForValue().get(PACKAGING_KEY_PREFIX + sessionId);
    if (json == null) {
      return null;
    }
    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> data = objectMapper.readValue(json, Map.class);
      return (String) data.get("packagingType");
    } catch (JsonProcessingException e) {
      log.error("포장 방식 데이터 파싱 실패", e);
      throw new CustomException(CartErrorCode.CART_DATA_CORRUPTED);
    }
  }

  private CartData getCartData(String sessionId) {
    String json = redisTemplate.opsForValue().get(CART_KEY_PREFIX + sessionId);
    if (json == null) {
      return emptyCart();
    }
    try {
      CartData cart = objectMapper.readValue(json, CartData.class);
      if (cart == null || cart.getItems() == null) {
        throw new CustomException(CartErrorCode.CART_DATA_CORRUPTED);
      }
      return cart;
    } catch (JsonProcessingException e) {
      log.error("장바구니 데이터 파싱 실패", e);
      throw new CustomException(CartErrorCode.CART_DATA_CORRUPTED);
    }
  }

  private void saveCartData(String sessionId, CartData cart) {
    cart.setUpdatedAt(now());
    try {
      redisTemplate.opsForValue().set(CART_KEY_PREFIX + sessionId,
          objectMapper.writeValueAsString(cart), CART_EXPIRE_HOURS, TimeUnit.HOURS);
    } catch (JsonProcessingException e) {
      log.error("장바구니 데이터 저장 실패", e);
      throw new CustomException(CartErrorCode.CART_SAVE_FAILED);
    }
  }

  private CartData emptyCart() {
    CartData cart = new CartData();
    cart.setCreatedAt(now());
    cart.setUpdatedAt(cart.getCreatedAt());
    return cart;
  }

  private String now() {
    return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
  }
}
