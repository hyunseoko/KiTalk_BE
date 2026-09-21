package likelion.kitalk.touch.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartEntry {
  private Long menuId;
  private Integer quantity;
}
