package likelion.kitalk.touch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import likelion.kitalk.touch.dto.CartData;
import likelion.kitalk.touch.dto.request.CartUpdateRequest;
import likelion.kitalk.touch.dto.response.CartResponse;
import org.mockito.ArgumentCaptor;
import likelion.kitalk.global.exception.CustomException;
import likelion.kitalk.touch.dto.request.CartRemoveRequest;
import likelion.kitalk.touch.exception.CartErrorCode;
import likelion.kitalk.touch.util.CartUtils;
import likelion.kitalk.touch.validator.CartValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {
  @Mock private RedisTemplate<String, String> redis;
  @Mock private ValueOperations<String, String> values;
  @Mock private CartValidator validator;
  @Mock private CartUtils utils;
  private CartService service;

  @BeforeEach
  void setUp() {
    service = new CartService(redis, new ObjectMapper(), validator, utils);
    when(redis.opsForValue()).thenReturn(values);
  }

  @Test
  void missingItemKeepsNotFoundError() {
    when(values.get("touch_cart:session")).thenReturn("{\"items\":[],\"createdAt\":\"now\",\"updatedAt\":\"now\"}");
    var request = CartRemoveRequest.builder().menuId(7L).build();

    CustomException error = assertThrows(CustomException.class,
        () -> service.removeMenuItem("session", request));

    assertEquals(CartErrorCode.CART_ITEM_NOT_FOUND, error.getErrorCode());
  }

  @Test
  void updateReplacesEntriesAndKeepsRedisFormat() throws Exception {
    when(values.get("touch_cart:session")).thenReturn(
        "{\"items\":[{\"menuId\":1,\"quantity\":2}],\"createdAt\":\"now\",\"updatedAt\":\"now\"}");
    when(utils.convertToCartItemDetails(anyList())).thenReturn(List.of());
    when(utils.convertToMap(any(CartResponse.class))).thenReturn(Map.of());
    var request = CartUpdateRequest.builder().orders(List.of(
        CartUpdateRequest.CartUpdateItem.builder().menu_id(2L).quantity(3).build())).build();

    service.updateCart("session", request);

    ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
    verify(values).set(eq("touch_cart:session"), json.capture(), eq(2L), eq(TimeUnit.HOURS));
    CartData saved = new ObjectMapper().readValue(json.getValue(), CartData.class);
    assertEquals(1, saved.getItems().size());
    assertEquals(2L, saved.getItems().get(0).getMenuId());
    assertEquals(3, saved.getItems().get(0).getQuantity());
  }

  @Test
  void corruptedCartKeepsDataError() {
    when(values.get("touch_cart:session")).thenReturn("{broken");

    CustomException error = assertThrows(CustomException.class,
        () -> service.getCart("session"));

    assertEquals(CartErrorCode.CART_DATA_CORRUPTED, error.getErrorCode());
  }
}
