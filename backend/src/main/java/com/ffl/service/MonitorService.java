package com.ffl.service;

import com.ffl.client.HnairClient.FlightQuote;
import com.ffl.client.HnairClient.ShopResult;
import com.ffl.client.ShopGate;
import com.ffl.entity.AppSetting;
import com.ffl.entity.Monitor;
import com.ffl.entity.MonitorEvent;
import com.ffl.entity.User;
import com.ffl.repository.MonitorEventRepository;
import com.ffl.repository.MonitorRepository;
import com.ffl.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class MonitorService {

    public static final int MAX_PER_USER = 10;
    private static final Pattern AIRPORT = Pattern.compile("^[A-Z]{3}$");
    private static final Pattern FLIGHT_NO = Pattern.compile("^[0-9]{1,4}$");

    private final MonitorRepository monitorRepository;
    private final MonitorEventRepository monitorEventRepository;
    private final UserRepository userRepository;
    private final SettingService settingService;
    private final ShopGate shopGate;
    private final MailService mailService;
    private final PlatformTransactionManager transactionManager;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(User user) {
        return monitorRepository.findByUserIdOrderByIdDesc(user.getId()).stream().map(this::toMap).toList();
    }

    @Transactional
    public Map<String, Object> create(User user, String origin, String destination, LocalDate date, String flightNo,
                                      Boolean notifyPresence, Boolean notifyQuantity) {
        userRepository.lockById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        if (monitorRepository.countByUserId(user.getId()) >= MAX_PER_USER) {
            throw new IllegalArgumentException("最多设置10条监控");
        }
        Monitor monitor = new Monitor();
        monitor.setUserId(user.getId());
        apply(monitor, origin, destination, date, flightNo);
        monitor.setNotifyPresence(Boolean.TRUE.equals(notifyPresence));
        monitor.setNotifyQuantity(Boolean.TRUE.equals(notifyQuantity));
        monitor.setEnabled(true);
        return toMap(monitorRepository.save(monitor));
    }

    @Transactional
    public Map<String, Object> update(User user, Long id, String origin, String destination,
                                      LocalDate date, String flightNo, Boolean enabled,
                                      Boolean notifyPresence, Boolean notifyQuantity) {
        Monitor monitor = monitorRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("监控不存在"));
        boolean identityChanged = identityChanged(monitor, origin, destination, date, flightNo);
        apply(monitor, origin, destination, date, flightNo);
        if (identityChanged) {
            monitor.setInventoryState(null);
            monitorEventRepository.deleteByMonitorId(monitor.getId());
        }
        if (enabled != null) {
            monitor.setEnabled(enabled);
        }
        if (notifyPresence != null) {
            monitor.setNotifyPresence(notifyPresence);
        }
        if (notifyQuantity != null) {
            monitor.setNotifyQuantity(notifyQuantity);
        }
        return toMap(monitorRepository.save(monitor));
    }

    @Transactional
    public void delete(User user, Long id) {
        Monitor monitor = monitorRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("监控不存在"));
        monitorEventRepository.deleteByMonitorId(monitor.getId());
        monitorRepository.delete(monitor);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> history(User user, Long id) {
        loadOwned(user, id);
        return monitorEventRepository.findByMonitorIdOrderByIdDesc(id).stream().map(this::eventToMap).toList();
    }

    @Transactional(readOnly = true)
    public Monitor loadOwned(User user, Long id) {
        return monitorRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("监控不存在"));
    }

    public boolean checkOne(Monitor requested) {
        AppSetting setting = settingService.current();
        ShopResult result = shopGate.search(requested.getOrigin(), requested.getDestination(), requested.getFlightDate());
        MailJob job = new TransactionTemplate(transactionManager).execute(status -> {
            Monitor current = monitorRepository.lockById(requested.getId()).orElse(null);
            if (current == null || !sameRoute(requested, current)) {
                log.info("Skip stale check result for monitor {}", requested.getId());
                return null;
            }
            return applyResult(current, result, setting.getTargetPrice());
        });
        if (job != null) {
            deliver(job);
        }
        return result.riskLimited();
    }

    private void deliver(MailJob job) {
        User user = userRepository.findById(job.userId()).orElse(null);
        if (user == null || !mailService.configured(user)) {
            return;
        }
        try {
            mailService.send(user, job.subject(), job.body());
            monitorEventRepository.findById(job.eventId()).ifPresent(event -> {
                String detail = event.getDetail();
                int mark = detail == null ? -1 : detail.indexOf("；邮件发送失败");
                if (mark >= 0) {
                    event.setDetail(detail.substring(0, mark));
                    monitorEventRepository.save(event);
                }
            });
        } catch (RuntimeException e) {
            monitorEventRepository.findById(job.eventId()).ifPresent(event -> {
                String detail = event.getDetail() == null ? "" : event.getDetail();
                int mark = detail.indexOf("；邮件发送失败");
                String base = mark >= 0 ? detail.substring(0, mark) : detail;
                event.setDetail(trim(base + "；邮件发送失败：" + e.getMessage(), 1000));
                monitorEventRepository.save(event);
            });
        }
    }

    private static boolean sameRoute(Monitor left, Monitor right) {
        return left.getOrigin().equals(right.getOrigin())
                && left.getDestination().equals(right.getDestination())
                && left.getFlightDate().equals(right.getFlightDate())
                && Objects.equals(left.getFlightNo(), right.getFlightNo());
    }

    private MailJob applyResult(Monitor monitor, ShopResult result, int targetPrice) {
        monitor.setLastCheckedAt(LocalDateTime.now());
        monitor.setLastSuccess(result.success());
        if (!result.success()) {
            monitor.setLastHit(false);
            monitor.setLastError(trim(result.errorMessage(), 300));
            monitor.setLastSummary(null);
            log.info("Monitor {} check failed: {}", monitor.getId(), result.errorMessage());
            return null;
        }
        monitor.setLastError(null);
        String wanted = normalizeFlightNo(monitor.getFlightNo());
        List<FlightQuote> scoped = result.quotes().stream()
                .filter(quote -> matchesFlight(quote, wanted))
                .toList();
        List<FlightQuote> matched = scoped.stream()
                .filter(quote -> priceEquals(quote.price(), targetPrice))
                .toList();
        monitor.setLastHit(!matched.isEmpty());
        if (!matched.isEmpty()) {
            monitor.setLastSummary(trim(summarize(matched), 500));
        } else if (scoped.isEmpty()) {
            monitor.setLastSummary(result.quotes().isEmpty() ? "无航班" : "无此航班");
        } else {
            monitor.setLastSummary(trim("未命中目标价" + targetPrice + "，" + summarize(cheapest(scoped)), 500));
        }
        MailJob job = recordStock(monitor, scoped, targetPrice);
        log.info("Monitor {} checked hit={} summary={}", monitor.getId(), monitor.getLastHit(), monitor.getLastSummary());
        return job;
    }

    private MailJob recordStock(Monitor monitor, List<FlightQuote> scoped, int targetPrice) {
        Map<String, String> current = new TreeMap<>();
        for (FlightQuote quote : scoped) {
            String key = digits(quote.flightNo());
            if (key.isBlank()) {
                continue;
            }
            String token = priceEquals(quote.price(), targetPrice) ? stockToken(quote.inventory()) : "";
            String existing = current.get(key);
            if (existing == null || existing.isEmpty()) {
                current.put(key, token);
            }
        }
        String encoded = encode(current);
        boolean first = monitor.getInventoryState() == null;
        Map<String, String> previous = first ? new TreeMap<>() : decode(monitor.getInventoryState());
        StringBuilder changes = new StringBuilder();
        boolean presence = false;
        boolean quantity = false;
        TreeSet<String> keys = new TreeSet<>();
        keys.addAll(previous.keySet());
        keys.addAll(current.keySet());
        for (String key : keys) {
            String before = previous.getOrDefault(key, "");
            String after = current.getOrDefault(key, "");
            if (before.equals(after)) {
                continue;
            }
            boolean beforeHas = !before.isEmpty();
            boolean afterHas = !after.isEmpty();
            if (beforeHas == afterHas) {
                if (!beforeHas) {
                    continue;
                }
                quantity = true;
                appendChange(changes, key + " 余" + before + "→" + after);
                continue;
            }
            presence = true;
            if (afterHas) {
                appendChange(changes, key + " 从无到有 余" + after);
            } else {
                appendChange(changes, key + " 从有到无 原余" + before);
            }
        }
        monitor.setInventoryState(encoded);
        if (!first && !presence && !quantity) {
            return retryFailedMail(monitor);
        }
        boolean mail = (presence && monitor.isNotifyPresence()) || (quantity && monitor.isNotifyQuantity());
        String detail = presence || quantity
                ? changes.toString()
                : (current.isEmpty() ? "无航班" : describe(current));
        return saveEvent(monitor, first, presence, quantity, detail, mail);
    }

    private MailJob retryFailedMail(Monitor monitor) {
        if (!monitor.isNotifyPresence() && !monitor.isNotifyQuantity()) {
            return null;
        }
        List<MonitorEvent> events = monitorEventRepository.findByMonitorIdOrderByIdDesc(monitor.getId());
        if (events.isEmpty() || events.get(0).getDetail() == null) {
            return null;
        }
        MonitorEvent event = events.get(0);
        int mark = event.getDetail().indexOf("；邮件发送失败");
        if (mark < 0) {
            return null;
        }
        User user = userRepository.findById(monitor.getUserId()).orElse(null);
        if (user == null || !mailService.configured(user)) {
            return null;
        }
        String text = event.getDetail().substring(0, mark);
        String route = monitor.getOrigin() + "-" + monitor.getDestination() + " " + monitor.getFlightDate();
        return new MailJob(event.getId(), user.getId(), "监控提醒 " + route,
                text + "\n" + route + (monitor.getFlightNo() == null ? "" : " " + monitor.getFlightNo()));
    }

    private MailJob saveEvent(Monitor monitor, boolean initial, boolean presence, boolean quantity,
                              String detail, boolean mail) {
        String text = trim(detail, 1000);
        MailJob job = null;
        if (mail) {
            User user = userRepository.findById(monitor.getUserId()).orElse(null);
            if (user == null || !mailService.configured(user)) {
                text = trim(text + "；未配置邮箱，未发送", 1000);
            } else {
                String route = monitor.getOrigin() + "-" + monitor.getDestination() + " " + monitor.getFlightDate();
                job = new MailJob(null, user.getId(), "监控提醒 " + route,
                        text + "\n" + route + (monitor.getFlightNo() == null ? "" : " " + monitor.getFlightNo()));
            }
        }
        MonitorEvent event = new MonitorEvent();
        event.setMonitorId(monitor.getId());
        event.setInitialRecord(initial);
        event.setPresenceChanged(presence);
        event.setQuantityChanged(quantity);
        event.setDetail(text);
        event = monitorEventRepository.save(event);
        log.info("Monitor {} recorded initial={} presence={} quantity={}", monitor.getId(), initial, presence, quantity);
        if (job == null) {
            return null;
        }
        return new MailJob(event.getId(), job.userId(), job.subject(), job.body());
    }

    private record MailJob(Long eventId, Long userId, String subject, String body) {
    }

    private static void appendChange(StringBuilder changes, String text) {
        if (!changes.isEmpty()) {
            changes.append('；');
        }
        changes.append(text);
    }

    private static String describe(Map<String, String> state) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : state.entrySet()) {
            appendChange(sb, entry.getValue().isEmpty()
                    ? entry.getKey() + " 无"
                    : entry.getKey() + " 余" + entry.getValue());
        }
        return sb.toString();
    }

    private static String encode(Map<String, String> state) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : state.entrySet()) {
            if (!sb.isEmpty()) {
                sb.append(';');
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return sb.toString();
    }

    private static Map<String, String> decode(String raw) {
        Map<String, String> state = new TreeMap<>();
        if (raw == null || raw.isBlank()) {
            return state;
        }
        for (String part : raw.split(";")) {
            int split = part.indexOf('=');
            if (split <= 0) {
                continue;
            }
            state.put(part.substring(0, split), part.substring(split + 1));
        }
        return state;
    }

    private static String stockToken(String inventory) {
        if (inventory == null) {
            return "";
        }
        String value = inventory.trim().toUpperCase(Locale.ROOT);
        if (value.isEmpty() || "0".equals(value)) {
            return "";
        }
        return value;
    }

    private static String summarize(List<FlightQuote> quotes) {
        StringBuilder sb = new StringBuilder();
        for (FlightQuote quote : quotes) {
            if (!sb.isEmpty()) {
                sb.append("；");
            }
            sb.append(quote.flightNo())
                    .append(' ')
                    .append(quote.depTime())
                    .append('-')
                    .append(quote.arrTime())
                    .append(" ¥")
                    .append(quote.price())
                    .append(" 余")
                    .append(quote.inventory());
        }
        return sb.toString();
    }

    private static boolean matchesFlight(FlightQuote quote, String wanted) {
        if (wanted == null) {
            return true;
        }
        return wanted.equals(digits(quote.flightNo()));
    }

    private static List<FlightQuote> cheapest(List<FlightQuote> quotes) {
        int min = quotes.stream()
                .map(FlightQuote::price)
                .filter(price -> price != null && price.matches("\\d+"))
                .mapToInt(Integer::parseInt)
                .min()
                .orElse(-1);
        if (min < 0) {
            return quotes;
        }
        return quotes.stream().filter(quote -> priceEquals(quote.price(), min)).toList();
    }

    private static boolean priceEquals(String price, int target) {
        return price != null && price.matches("\\d+") && Integer.parseInt(price) == target;
    }

    private void apply(Monitor monitor, String origin, String destination, LocalDate date, String flightNo) {
        String from = normalizeAirport(origin);
        String to = normalizeAirport(destination);
        if (!AIRPORT.matcher(from).matches() || !AIRPORT.matcher(to).matches()) {
            throw new IllegalArgumentException("机场三字码需为3位字母");
        }
        if (from.equals(to)) {
            throw new IllegalArgumentException("出发和到达机场不能相同");
        }
        if (date == null) {
            throw new IllegalArgumentException("请填写日期");
        }
        String flight = normalizeFlightNo(flightNo);
        if (flight != null && !FLIGHT_NO.matcher(flight).matches()) {
            throw new IllegalArgumentException("航班号只填数字");
        }
        monitor.setOrigin(from);
        monitor.setDestination(to);
        monitor.setFlightDate(date);
        monitor.setFlightNo(flight);
    }

    private static String normalizeAirport(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeFlightNo(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String compact = value.trim().replace(" ", "");
        return compact.isEmpty() ? null : compact;
    }

    private boolean identityChanged(Monitor monitor, String origin, String destination, LocalDate date, String flightNo) {
        return !normalizeAirport(origin).equals(monitor.getOrigin())
                || !normalizeAirport(destination).equals(monitor.getDestination())
                || !Objects.equals(date, monitor.getFlightDate())
                || !Objects.equals(normalizeFlightNo(flightNo), monitor.getFlightNo());
    }

    private static String digits(String flightNo) {
        return flightNo == null ? "" : flightNo.replaceAll("[^0-9]", "");
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private Map<String, Object> toMap(Monitor monitor) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", monitor.getId());
        map.put("origin", monitor.getOrigin());
        map.put("destination", monitor.getDestination());
        map.put("flightDate", monitor.getFlightDate());
        map.put("flightNo", monitor.getFlightNo());
        map.put("notifyPresence", monitor.isNotifyPresence());
        map.put("notifyQuantity", monitor.isNotifyQuantity());
        map.put("enabled", monitor.isEnabled());
        map.put("createdAt", monitor.getCreatedAt());
        map.put("lastCheckedAt", monitor.getLastCheckedAt());
        map.put("lastSuccess", monitor.getLastSuccess());
        map.put("lastHit", monitor.getLastHit());
        map.put("lastSummary", monitor.getLastSummary());
        map.put("lastError", monitor.getLastError());
        return map;
    }

    private Map<String, Object> eventToMap(MonitorEvent event) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", event.getId());
        map.put("createdAt", event.getCreatedAt());
        map.put("initialRecord", event.isInitialRecord());
        map.put("presenceChanged", event.isPresenceChanged());
        map.put("quantityChanged", event.isQuantityChanged());
        map.put("detail", event.getDetail());
        return map;
    }
}
