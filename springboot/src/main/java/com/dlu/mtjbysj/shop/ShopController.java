package com.dlu.mtjbysj.shop;

import com.alipay.api.AlipayApiException;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.dlu.mtjbysj.knowledge.Fzwp;
import com.dlu.mtjbysj.knowledge.FzwpRepository;
import com.dlu.mtjbysj.user.User;
import com.dlu.mtjbysj.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/shop")
@CrossOrigin
@RequiredArgsConstructor
@SuppressWarnings("null")
public class ShopController {

    private final UserRepository userRepository;
    private final FzwpRepository fzwpRepository;
    private final CartItemRepository cartItemRepository;
    private final FavoriteItemRepository favoriteItemRepository;
    private final ShopOrderRepository shopOrderRepository;
    private final ShopOrderItemRepository shopOrderItemRepository;
    private final AlipayConfig alipayConfig;

    private User currentUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new IllegalStateException("未登录");
        }
        Object attr = session.getAttribute("LOGIN_USER");
        if (!(attr instanceof Map)) {
            throw new IllegalStateException("未登录");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) attr;
        Object usernameObj = map.get("username");
        if (usernameObj == null) {
            throw new IllegalStateException("未登录");
        }
        String username = usernameObj.toString();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("用户不存在"));
    }

    private boolean isAdmin(User user) {
        return user != null && "admin".equalsIgnoreCase(user.getRole());
    }

    @PostMapping("/cart/add")
    public ResponseEntity<?> addToCart(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        User user;
        try {
            user = currentUser(request);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }

        Object idObj = body.get("itemId");
        if (!(idObj instanceof Number)) {
            return ResponseEntity.badRequest().body(Map.of("error", "缺少物品ID"));
        }
        long itemId = ((Number) idObj).longValue();
        Optional<Fzwp> optItem = fzwpRepository.findById(itemId);
        if (optItem.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "物品不存在"));
        }
        Fzwp item = optItem.get();

        int quantity = 1;
        Object qObj = body.get("quantity");
        if (qObj instanceof Number) {
            quantity = ((Number) qObj).intValue();
        }
        if (quantity <= 0) {
            quantity = 1;
        }

        Optional<CartItem> existingOpt = cartItemRepository.findByUserAndItem(user, item);
        CartItem cartItem;
        if (existingOpt.isPresent()) {
            cartItem = existingOpt.get();
            cartItem.setQuantity(cartItem.getQuantity() + quantity);
        } else {
            cartItem = CartItem.builder()
                    .user(user)
                    .item(item)
                    .quantity(quantity)
                    .createdAt(LocalDateTime.now())
                    .build();
        }
        CartItem saved = cartItemRepository.save(cartItem);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", saved.getId());
        resp.put("itemId", item.getId());
        resp.put("itemName", item.getItemName());
        resp.put("quantity", saved.getQuantity());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/cart")
    public ResponseEntity<?> listCart(HttpServletRequest request) {
        User user;
        try {
            user = currentUser(request);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }
        List<CartItem> items = cartItemRepository.findByUserOrderByCreatedAtDesc(user);
        List<Map<String, Object>> list = new ArrayList<>();
        for (CartItem ci : items) {
            Fzwp item = ci.getItem();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", ci.getId());
            m.put("itemId", item.getId());
            m.put("itemName", item.getItemName());
            m.put("plantName", item.getPlantName());
            m.put("price", item.getPrice());
            m.put("imageUrl", item.getImageUrl());
            m.put("targetDisease", item.getTargetDisease());
            m.put("quantity", ci.getQuantity());
            list.add(m);
        }
        return ResponseEntity.ok(list);
    }

    @DeleteMapping("/cart/{id}")
    public ResponseEntity<?> deleteCartItem(@PathVariable Long id, HttpServletRequest request) {
        User user;
        try {
            user = currentUser(request);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }
        Optional<CartItem> opt = cartItemRepository.findById(id);
        if (opt.isEmpty() || !Objects.equals(opt.get().getUser().getId(), user.getId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "记录不存在"));
        }
        cartItemRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/favorites/toggle")
    public ResponseEntity<?> toggleFavorite(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        User user;
        try {
            user = currentUser(request);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }

        Object idObj = body.get("itemId");
        if (!(idObj instanceof Number)) {
            return ResponseEntity.badRequest().body(Map.of("error", "缺少物品ID"));
        }
        long itemId = ((Number) idObj).longValue();
        Optional<Fzwp> optItem = fzwpRepository.findById(itemId);
        if (optItem.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "物品不存在"));
        }
        Fzwp item = optItem.get();

        Optional<FavoriteItem> existingOpt = favoriteItemRepository.findByUserAndItem(user, item);
        boolean favorited;
        if (existingOpt.isPresent()) {
            favoriteItemRepository.delete(existingOpt.get());
            favorited = false;
        } else {
            FavoriteItem f = FavoriteItem.builder()
                    .user(user)
                    .item(item)
                    .createdAt(LocalDateTime.now())
                    .build();
            favoriteItemRepository.save(f);
            favorited = true;
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("itemId", item.getId());
        resp.put("itemName", item.getItemName());
        resp.put("favorited", favorited);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/favorites")
    public ResponseEntity<?> listFavorites(HttpServletRequest request) {
        User user;
        try {
            user = currentUser(request);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }
        List<FavoriteItem> list = favoriteItemRepository.findByUserOrderByCreatedAtDesc(user);
        List<Map<String, Object>> out = new ArrayList<>();
        for (FavoriteItem fi : list) {
            Fzwp item = fi.getItem();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", fi.getId());
            m.put("itemId", item.getId());
            m.put("itemName", item.getItemName());
            m.put("plantName", item.getPlantName());
            m.put("price", item.getPrice());
            m.put("imageUrl", item.getImageUrl());
            m.put("targetDisease", item.getTargetDisease());
            m.put("createdAt", fi.getCreatedAt());
            out.add(m);
        }
        return ResponseEntity.ok(out);
    }

    @PostMapping("/orders/buy-now")
    public ResponseEntity<?> buyNow(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        User user;
        try {
            user = currentUser(request);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }

        String shippingAddress = Optional.ofNullable(user.getAddress()).map(String::trim).orElse("");
        if (shippingAddress.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "请填写收货地址以购买"));
        }

        Object idObj = body.get("itemId");
        if (!(idObj instanceof Number)) {
            return ResponseEntity.badRequest().body(Map.of("error", "缺少物品ID"));
        }
        long itemId = ((Number) idObj).longValue();
        Optional<Fzwp> optItem = fzwpRepository.findById(itemId);
        if (optItem.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "物品不存在"));
        }
        Fzwp item = optItem.get();

        int quantity = 1;
        Object qObj = body.get("quantity");
        if (qObj instanceof Number) {
            quantity = ((Number) qObj).intValue();
        }
        if (quantity <= 0) {
            quantity = 1;
        }

        BigDecimal unitPrice = item.getPrice() != null ? item.getPrice() : BigDecimal.ZERO;
        BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));

        ShopOrder order = ShopOrder.builder()
                .user(user)
                .createdAt(LocalDateTime.now())
                .totalAmount(lineTotal)
                .status("CREATED")
                .shippingAddress(shippingAddress)
                .build();
        order = shopOrderRepository.save(order);

        ShopOrderItem orderItem = ShopOrderItem.builder()
                .order(order)
                .item(item)
                .quantity(quantity)
                .unitPrice(unitPrice)
                .lineTotal(lineTotal)
                .build();
        shopOrderItemRepository.save(orderItem);

        Map<String, Object> itemDto = new LinkedHashMap<>();
        itemDto.put("itemId", item.getId());
        itemDto.put("itemName", item.getItemName());
        itemDto.put("quantity", quantity);
        itemDto.put("unitPrice", unitPrice);
        itemDto.put("lineTotal", lineTotal);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("orderId", order.getId());
        resp.put("totalAmount", lineTotal);
        resp.put("status", order.getStatus());
        resp.put("items", List.of(itemDto));
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/cart/checkout")
    public ResponseEntity<?> checkoutCart(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        User user;
        try {
            user = currentUser(request);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }

        Object idsObj = body.get("cartItemIds");
        if (!(idsObj instanceof List)) {
            return ResponseEntity.badRequest().body(Map.of("error", "缺少购物车条目ID列表"));
        }
        @SuppressWarnings("unchecked")
        List<Object> rawList = (List<Object>) idsObj;
        if (rawList.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "购物车条目列表不能为空"));
        }

        List<Long> cartIds = new ArrayList<>();
        for (Object o : rawList) {
            if (o instanceof Number) {
                cartIds.add(((Number) o).longValue());
            }
        }
        if (cartIds.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "购物车条目列表无效"));
        }

        List<CartItem> items = cartItemRepository.findAllById(cartIds);
        if (items.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "购物车记录不存在"));
        }

        List<CartItem> owned = new ArrayList<>();
        for (CartItem ci : items) {
            if (ci.getUser() != null && Objects.equals(ci.getUser().getId(), user.getId())) {
                owned.add(ci);
            }
        }
        if (owned.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "购物车记录不存在"));
        }

        String shippingAddress = Optional.ofNullable(user.getAddress()).map(String::trim).orElse("");
        if (shippingAddress.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "请填写收货地址以购买"));
        }

        BigDecimal total = BigDecimal.ZERO;
        List<Map<String, Object>> itemDtos = new ArrayList<>();
        for (CartItem ci : owned) {
            Fzwp item = ci.getItem();
            if (item == null) {
                continue;
            }
            BigDecimal unitPrice = item.getPrice() != null ? item.getPrice() : BigDecimal.ZERO;
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(ci.getQuantity()));
            total = total.add(lineTotal);

            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("itemId", item.getId());
            dto.put("itemName", item.getItemName());
            dto.put("quantity", ci.getQuantity());
            dto.put("unitPrice", unitPrice);
            dto.put("lineTotal", lineTotal);
            itemDtos.add(dto);
        }

        if (total.compareTo(BigDecimal.ZERO) <= 0 || itemDtos.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "购物车中无有效商品"));
        }

        ShopOrder order = ShopOrder.builder()
                .user(user)
                .createdAt(LocalDateTime.now())
                .totalAmount(total)
                .status("CREATED")
                .shippingAddress(shippingAddress)
                .build();
        order = shopOrderRepository.save(order);

        for (CartItem ci : owned) {
            Fzwp item = ci.getItem();
            if (item == null) {
                continue;
            }
            BigDecimal unitPrice = item.getPrice() != null ? item.getPrice() : BigDecimal.ZERO;
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(ci.getQuantity()));
            ShopOrderItem oi = ShopOrderItem.builder()
                    .order(order)
                    .item(item)
                    .quantity(ci.getQuantity())
                    .unitPrice(unitPrice)
                    .lineTotal(lineTotal)
                    .build();
            shopOrderItemRepository.save(oi);
        }

        cartItemRepository.deleteAll(owned);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("orderId", order.getId());
        resp.put("totalAmount", order.getTotalAmount());
        resp.put("status", order.getStatus());
        resp.put("items", itemDtos);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/orders")
    public ResponseEntity<?> listOrders(HttpServletRequest request) {
        User user;
        try {
            user = currentUser(request);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }
        List<ShopOrder> orders = shopOrderRepository.findByUserOrderByCreatedAtDesc(user);
        List<Map<String, Object>> out = new ArrayList<>();
        for (ShopOrder order : orders) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", order.getId());
            m.put("createdAt", order.getCreatedAt());
            m.put("status", order.getStatus());
            m.put("totalAmount", order.getTotalAmount());
            m.put("shippingAddress", order.getShippingAddress());
            List<ShopOrderItem> items = shopOrderItemRepository.findByOrder(order);
            List<Map<String, Object>> itemDtos = new ArrayList<>();
            for (ShopOrderItem oi : items) {
                Fzwp item = oi.getItem();
                Map<String, Object> im = new LinkedHashMap<>();
                im.put("itemId", item.getId());
                im.put("itemName", item.getItemName());
                im.put("quantity", oi.getQuantity());
                im.put("unitPrice", oi.getUnitPrice());
                im.put("lineTotal", oi.getLineTotal());
                itemDtos.add(im);
            }
            m.put("items", itemDtos);
            out.add(m);
        }
        return ResponseEntity.ok(out);
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<?> getOrderDetail(@PathVariable Long id, HttpServletRequest request) {
        User user;
        try {
            user = currentUser(request);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }

        Optional<ShopOrder> opt = shopOrderRepository.findById(id);
        if (opt.isEmpty() || !Objects.equals(opt.get().getUser().getId(), user.getId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "订单不存在"));
        }
        ShopOrder order = opt.get();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", order.getId());
        m.put("createdAt", order.getCreatedAt());
        m.put("status", order.getStatus());
        m.put("totalAmount", order.getTotalAmount());
        m.put("shippingAddress", order.getShippingAddress());
        List<ShopOrderItem> items = shopOrderItemRepository.findByOrder(order);
        List<Map<String, Object>> itemDtos = new ArrayList<>();
        for (ShopOrderItem oi : items) {
            Fzwp item = oi.getItem();
            Map<String, Object> im = new LinkedHashMap<>();
            im.put("itemId", item.getId());
            im.put("itemName", item.getItemName());
            im.put("quantity", oi.getQuantity());
            im.put("unitPrice", oi.getUnitPrice());
            im.put("lineTotal", oi.getLineTotal());
            itemDtos.add(im);
        }
        m.put("items", itemDtos);
        return ResponseEntity.ok(m);
    }

    @PostMapping("/orders/{id}/pay")
    public ResponseEntity<?> payOrder(@PathVariable Long id, HttpServletRequest request) {
        User user;
        try {
            user = currentUser(request);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }

        Optional<ShopOrder> opt = shopOrderRepository.findById(id);
        if (opt.isEmpty() || !Objects.equals(opt.get().getUser().getId(), user.getId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "订单不存在"));
        }
        ShopOrder order = opt.get();
        if (!"CREATED".equals(order.getStatus())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "当前订单状态不可支付"));
        }
        order.setStatus("PAID");
        shopOrderRepository.save(order);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", order.getId());
        resp.put("status", order.getStatus());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/pay/alipay")
    public void alipayPcPay(@RequestParam("orderId") Long orderId,
                            HttpServletRequest servletRequest,
                            HttpServletResponse response) throws java.io.IOException {
        User user;
        try {
            user = currentUser(servletRequest);
        } catch (IllegalStateException e) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json;charset=" + alipayConfig.getCharset());
            response.getWriter().write("{\"error\":\"" + e.getMessage() + "\"}");
            return;
        }

        if (orderId == null) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            response.setContentType("application/json;charset=" + alipayConfig.getCharset());
            response.getWriter().write("{\"error\":\"缺少订单ID\"}");
            return;
        }

        Optional<ShopOrder> opt = shopOrderRepository.findById(orderId);
        if (opt.isEmpty() || opt.get().getUser() == null
                || !Objects.equals(opt.get().getUser().getId(), user.getId())) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            response.setContentType("application/json;charset=" + alipayConfig.getCharset());
            response.getWriter().write("{\"error\":\"订单不存在\"}");
            return;
        }
        ShopOrder order = opt.get();
        if (!"CREATED".equals(order.getStatus())) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            response.setContentType("application/json;charset=" + alipayConfig.getCharset());
            response.getWriter().write("{\"error\":\"当前订单状态不可支付\"}");
            return;
        }

        String totalAmount = order.getTotalAmount() != null
                ? order.getTotalAmount().toPlainString()
                : "0.00";
        String subject = "防治物品订单-" + order.getId();

        AlipayTradePagePayRequest alipayRequest = new AlipayTradePagePayRequest();
        alipayRequest.setReturnUrl(alipayConfig.getReturnUrl());
        String notifyUrl = alipayConfig.getNotifyUrl();
        if (notifyUrl != null && !notifyUrl.isBlank()) {
            alipayRequest.setNotifyUrl(notifyUrl);
        }
        String bizContent = "{" +
                "\"out_trade_no\":\"" + order.getId() + "\"," +
                "\"product_code\":\"FAST_INSTANT_TRADE_PAY\"," +
                "\"total_amount\":\"" + totalAmount + "\"," +
                "\"subject\":\"" + subject.replace("\"", "") + "\"" +
                "}";
        alipayRequest.setBizContent(bizContent);

        try {
            String form = alipayConfig.getClient().pageExecute(alipayRequest).getBody();
            response.setContentType("text/html;charset=" + alipayConfig.getCharset());
            response.getWriter().write(form);
            response.getWriter().flush();
        } catch (AlipayApiException ex) {
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setContentType("application/json;charset=" + alipayConfig.getCharset());
            String msg = ex.getMessage() != null ? ex.getMessage() : "调用支付宝支付失败";
            response.getWriter().write("{\"error\":\"" + msg.replace("\"", "") + "\"}");
        }
    }

    @GetMapping("/admin/orders")
    public ResponseEntity<?> adminListOrders(HttpServletRequest request) {
        User user;
        try {
            user = currentUser(request);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }
        if (!isAdmin(user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "无权访问"));
        }

        List<ShopOrder> orders = shopOrderRepository.findAll();
        orders.sort((a, b) -> {
            if (a.getCreatedAt() == null || b.getCreatedAt() == null) return 0;
            return b.getCreatedAt().compareTo(a.getCreatedAt());
        });

        List<Map<String, Object>> out = new ArrayList<>();
        for (ShopOrder order : orders) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", order.getId());
            m.put("createdAt", order.getCreatedAt());
            m.put("status", order.getStatus());
            m.put("totalAmount", order.getTotalAmount());
            m.put("shippingAddress", order.getShippingAddress());
            User u = order.getUser();
            if (u != null) {
                m.put("username", u.getUsername());
                m.put("nickname", u.getNickname());
            }
            out.add(m);
        }
        return ResponseEntity.ok(out);
    }

    @PostMapping("/admin/orders/{id}/complete")
    public ResponseEntity<?> adminCompleteOrder(@PathVariable Long id, HttpServletRequest request) {
        User user;
        try {
            user = currentUser(request);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }
        if (!isAdmin(user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "无权访问"));
        }

        Optional<ShopOrder> opt = shopOrderRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "订单不存在"));
        }
        ShopOrder order = opt.get();
        if (!"PAID".equals(order.getStatus())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "当前订单状态不可确认收货"));
        }
        order.setStatus("COMPLETED");
        shopOrderRepository.save(order);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", order.getId());
        resp.put("status", order.getStatus());
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/orders/{id}/complete")
    public ResponseEntity<?> completeOrder(@PathVariable Long id, HttpServletRequest request) {
        User user;
        try {
            user = currentUser(request);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }

        Optional<ShopOrder> opt = shopOrderRepository.findById(id);
        if (opt.isEmpty() || !Objects.equals(opt.get().getUser().getId(), user.getId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "订单不存在"));
        }
        ShopOrder order = opt.get();
        if (!"PAID".equals(order.getStatus())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "当前订单状态不可确认收货"));
        }
        order.setStatus("COMPLETED");
        shopOrderRepository.save(order);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", order.getId());
        resp.put("status", order.getStatus());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/pay/alipay/return")
    public void alipayReturn(HttpServletRequest request, HttpServletResponse response) throws java.io.IOException {
        request.setCharacterEncoding(alipayConfig.getCharset());
        Map<String, String[]> requestParams = request.getParameterMap();
        Map<String, String> params = new HashMap<>();
        for (String name : requestParams.keySet()) {
            String[] values = requestParams.get(name);
            if (values == null) {
                continue;
            }
            String valueStr = String.join(",", values);
            params.put(name, valueStr);
        }

        boolean signVerified;
        try {
            signVerified = AlipaySignature.rsaCheckV1(
                    params,
                    alipayConfig.getAlipayPublicKey(),
                    alipayConfig.getCharset(),
                    alipayConfig.getSignType());
        } catch (AlipayApiException e) {
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.getWriter().write("验签异常");
            return;
        }

        Long orderId = null;
        if (signVerified) {
            String outTradeNo = request.getParameter("out_trade_no");
            if (outTradeNo != null && !outTradeNo.isBlank()) {
                try {
                    orderId = Long.parseLong(outTradeNo);
                } catch (NumberFormatException ignore) {
                    // ignore
                }
            }
            if (orderId != null) {
                Optional<ShopOrder> opt = shopOrderRepository.findById(orderId);
                if (opt.isPresent()) {
                    ShopOrder order = opt.get();
                    if ("CREATED".equals(order.getStatus())) {
                        order.setStatus("PAID");
                        shopOrderRepository.save(order);
                    }
                }
            }
        }

        if (orderId == null) {
            response.getWriter().write(signVerified ? "支付成功，但未找到订单" : "验签失败");
            return;
        }

        String redirectUrl = alipayConfig.getFrontendReturnBase() + "/shop/pay/result/" + orderId;
        response.sendRedirect(redirectUrl);
    }

    @PostMapping("/pay/alipay/notify")
    public String alipayNotify(HttpServletRequest request) throws java.io.UnsupportedEncodingException {
        request.setCharacterEncoding(alipayConfig.getCharset());
        Map<String, String[]> requestParams = request.getParameterMap();
        Map<String, String> params = new HashMap<>();
        for (String name : requestParams.keySet()) {
            String[] values = requestParams.get(name);
            if (values == null) {
                continue;
            }
            String valueStr = String.join(",", values);
            params.put(name, valueStr);
        }

        boolean signVerified;
        try {
            signVerified = AlipaySignature.rsaCheckV1(
                    params,
                    alipayConfig.getAlipayPublicKey(),
                    alipayConfig.getCharset(),
                    alipayConfig.getSignType());
        } catch (AlipayApiException e) {
            return "fail";
        }
        if (!signVerified) {
            return "fail";
        }

        String outTradeNo = request.getParameter("out_trade_no");
        if (outTradeNo != null && !outTradeNo.isBlank()) {
            try {
                Long orderId = Long.parseLong(outTradeNo);
                Optional<ShopOrder> opt = shopOrderRepository.findById(orderId);
                if (opt.isPresent()) {
                    ShopOrder order = opt.get();
                    if ("CREATED".equals(order.getStatus())) {
                        order.setStatus("PAID");
                        shopOrderRepository.save(order);
                    }
                }
            } catch (NumberFormatException ignore) {
                // ignore
            }
        }

        return "success";
    }
}
