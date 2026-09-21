package likelion.kitalk.touch.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class CartData {
  private List<CartEntry> items = new ArrayList<>();
  private String createdAt;
  private String updatedAt;
}
