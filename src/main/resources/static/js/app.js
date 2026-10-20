const view = document.getElementById("view");
const accountEl = document.getElementById("account");
const TOKEN_KEY = "honhaeng.token";
const PASSWORD_RULE = /^(?=.*[A-Za-z])(?=.*\d)[\x21-\x7E]{8,64}$/;
const PASSWORD_HINT = "영문과 숫자를 섞어 8자 이상으로 만들어 주세요.";

let me = null;

const DINING = {
  YES: { text: "혼밥 OK", cls: "yes" },
  COUNTER: { text: "카운터·1인석", cls: "counter" },
  NO: { text: "혼자면 살짝 부담", cls: "no" },
  UNKNOWN: { text: "아직 정보 없어요", cls: "unknown" },
};
const CATEGORY = { RESTAURANT: "식당", CAFE: "카페", SPOT: "스팟" };

/* ---------- 공통 도우미 ---------- */

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function parseHash() {
  const raw = location.hash.replace(/^#\/?/, "");
  const [path, query = ""] = raw.split("?");
  const [page, id, sub] = path.split("/");
  return { page: page || "home", id, sub, params: new URLSearchParams(query) };
}

function setTab(page) {
  const tabs = ["home", "food", "nearby", "spot", "companion"];
  const tab = tabs.includes(page) ? page : "";
  document.querySelectorAll(".tabbar a").forEach((el) => {
    el.classList.toggle("on", el.dataset.tab === tab);
  });
}

function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

async function api(path, options = {}) {
  const headers = { "Content-Type": "application/json", ...(options.headers || {}) };
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;
  const res = await fetch(path, { ...options, headers });
  if (!res.ok) {
    let detail = "잠시 후 다시 시도해 주세요.";
    try {
      const body = await res.json();
      if (body.detail) detail = body.detail;
    } catch {
      /* 본문이 JSON 이 아니면 기본 문구를 씁니다. */
    }
    const error = new Error(detail);
    error.status = res.status;
    throw error;
  }
  const text = await res.text();
  return text ? JSON.parse(text) : null;
}

function saveSession(auth) {
  localStorage.setItem(TOKEN_KEY, auth.token);
  me = auth.member;
  renderAccount();
}

function clearSession() {
  localStorage.removeItem(TOKEN_KEY);
  me = null;
  renderAccount();
}

async function loadMe() {
  if (!getToken()) {
    me = null;
    return;
  }
  try {
    me = await api("/api/auth/me");
  } catch (error) {
    if (error.status === 401) clearSession();
  }
}

function renderAccount() {
  accountEl.innerHTML = me
    ? `${me.role === "ADMIN" ? `<a class="pill admin-pill" href="#/admin">관리자</a>` : ""}<a class="pill" href="#/me">${escapeHtml(me.nickname)}님</a>`
    : `<a class="pill" href="#/login">로그인</a><a class="pill solid" href="#/signup">회원가입</a>`;
}

function loginUrl(next) {
  return `#/login?next=${encodeURIComponent(next || location.hash || "#/")}`;
}

/** 로그인이 풀렸으면(401) 로그인 화면으로 보냅니다. 처리했으면 true. */
function handleAuthError(error) {
  if (error.status === 401) {
    clearSession();
    location.hash = loginUrl();
    return true;
  }
  return false;
}

function safeNext(next) {
  return next && next.startsWith("#/") ? next : "#/";
}

function loginPrompt(message) {
  return `
    <div class="notice">
      <p>${message}</p>
      <a class="primary block" href="${loginUrl()}">로그인하고 시작하기</a>
    </div>
  `;
}

function badge(code) {
  const item = DINING[code] || DINING.UNKNOWN;
  return `<span class="badge ${item.cls}">${item.text}</span>`;
}

function tagList(list) {
  return (list || []).map((t) => `<span class="tag">${escapeHtml(t)}</span>`).join("");
}

function shortDate(iso) {
  const [, m, d] = iso.split("-");
  return `${Number(m)}/${Number(d)}`;
}

function dateRange(post) {
  const nights = post.nights;
  const span = nights === 0 ? "당일치기" : `${nights}박 ${nights + 1}일`;
  const range = post.startDate === post.endDate
    ? shortDate(post.startDate)
    : `${shortDate(post.startDate)} ~ ${shortDate(post.endDate)}`;
  return `${range} · ${span}`;
}

function todayIso() {
  const d = new Date();
  const pad = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

function bindPasswordToggle(form) {
  const toggle = form.querySelector("[data-toggle-password]");
  if (!toggle) return;
  toggle.addEventListener("change", () => {
    form.querySelectorAll("input[data-password]").forEach((input) => {
      input.type = toggle.checked ? "text" : "password";
    });
  });
}

function showError(el, message) {
  el.hidden = false;
  el.textContent = message;
}

/* ---------- 휴대폰 인증 · 매너 ---------- */

const MANNER_TAGS = [
  ["PUNCTUAL", "시간 약속을 잘 지켜요"],
  ["KIND", "친절하고 매너 있어요"],
  ["COMFORTABLE", "대화가 편해요"],
  ["FAIR", "비용 정산이 깔끔해요"],
  ["AGAIN", "또 같이 가고 싶어요"],
  ["LATE", "약속 시간을 안 지켰어요"],
  ["NO_SHOW", "약속 장소에 나오지 않았어요"],
  ["RUDE", "불쾌한 언행이 있었어요"],
];
const APPLICATION_STATUS = {
  PENDING: { text: "검토 중", cls: "unknown" },
  ACCEPTED: { text: "수락됨", cls: "yes" },
  REJECTED: { text: "거절됨", cls: "no" },
};

function verifiedBadge(ok) {
  return ok
    ? `<span class="badge verified">휴대폰 인증</span>`
    : `<span class="badge unknown">미인증</span>`;
}

function temperatureClass(t) {
  if (t >= 40) return "hot";
  if (t >= 36.5) return "warm";
  if (t >= 33) return "cool";
  return "cold";
}

/** 동행 온도(36.5°C 시작) + 별점 요약 */
function mannerLine(manner) {
  const m = manner || { temperature: 36.5, count: 0 };
  const temp = `<span class="temp ${temperatureClass(m.temperature)}">동행 온도 ${m.temperature.toFixed(1)}°C</span>`;
  if (!m.count) return `${temp} 아직 함께한 기록이 없어요`;
  const stars = m.average ? `★ ${m.average.toFixed(1)} · ` : "";
  const tags = m.topTags.length ? ` · ${m.topTags.map(escapeHtml).join(", ")}` : "";
  return `${temp} ${stars}평가 ${m.count}개${tags}`;
}

function formatPhone(value) {
  const d = value.replace(/\D/g, "").slice(0, 11);
  if (d.length < 4) return d;
  if (d.length < 8) return `${d.slice(0, 3)}-${d.slice(3)}`;
  return `${d.slice(0, 3)}-${d.slice(3, d.length - 4)}-${d.slice(-4)}`;
}

function phoneVerifyBlock() {
  return `
    <div class="field phone-verify">
      <span>휴대폰 번호</span>
      <div class="row wide-btn">
        <input type="tel" name="phone" inputmode="numeric" autocomplete="tel" placeholder="010-1234-5678" maxlength="13">
        <button class="ghost" type="button" data-send>인증번호 받기</button>
      </div>
      <div class="row wide-btn" data-code-row hidden>
        <input name="code" inputmode="numeric" autocomplete="one-time-code" maxlength="6" placeholder="인증번호 6자리">
        <button class="primary" type="button" data-verify>확인</button>
      </div>
      <p class="hint" data-phone-hint>같이가요 안전을 위해 휴대폰 인증이 필요해요. 번호는 암호화해서 중복 가입 확인에만 써요.</p>
      <a class="hint dev-inbox" href="#/dev/sms" target="_blank" rel="noopener" data-dev-inbox hidden>개발용 문자함 열기 →</a>
    </div>
  `;
}

/** 인증번호 발송·확인을 연결하고, 인증이 끝나면 state.phone / state.token 이 채워집니다. */
function bindPhoneVerify(root) {
  const state = { phone: null, token: null };
  const phoneInput = root.querySelector("input[name=phone]");
  const codeInput = root.querySelector("input[name=code]");
  const sendBtn = root.querySelector("[data-send]");
  const verifyBtn = root.querySelector("[data-verify]");
  const codeRow = root.querySelector("[data-code-row]");
  const hint = root.querySelector("[data-phone-hint]");
  let timer = null;

  const setHint = (text, cls = "") => {
    hint.className = `hint ${cls}`.trim();
    hint.textContent = text;
  };
  const stopTimer = () => {
    if (timer) clearInterval(timer);
    timer = null;
  };

  phoneInput.addEventListener("input", () => {
    phoneInput.value = formatPhone(phoneInput.value);
  });

  sendBtn.addEventListener("click", async () => {
    sendBtn.disabled = true;
    try {
      const res = await api("/api/auth/phone/send", {
        method: "POST",
        body: JSON.stringify({ phone: phoneInput.value }),
      });
      codeRow.hidden = false;
      codeInput.value = "";
      codeInput.focus();
      sendBtn.textContent = "다시 받기";
      const devNote = res.devCode ? ` [개발 모드] 인증번호: ${res.devCode}` : "";
      root.querySelector("[data-dev-inbox]").hidden = !res.devCode;
      let left = res.expiresIn;
      stopTimer();
      const tick = () => {
        if (!document.body.contains(hint)) return stopTimer();
        if (left <= 0) {
          stopTimer();
          setHint("인증번호가 만료됐어요. 다시 받아 주세요.", "bad");
          return;
        }
        const mm = Math.floor(left / 60);
        const ss = String(left % 60).padStart(2, "0");
        setHint(`인증번호를 보냈어요. ${mm}:${ss} 안에 입력해 주세요.${devNote}`);
        left -= 1;
      };
      tick();
      timer = setInterval(tick, 1000);
      setTimeout(() => { sendBtn.disabled = false; }, 60000);
    } catch (err) {
      setHint(err.message, "bad");
      sendBtn.disabled = false;
    }
  });

  verifyBtn.addEventListener("click", async () => {
    verifyBtn.disabled = true;
    try {
      const res = await api("/api/auth/phone/verify", {
        method: "POST",
        body: JSON.stringify({ phone: phoneInput.value, code: codeInput.value.trim() }),
      });
      stopTimer();
      state.phone = phoneInput.value;
      state.token = res.verificationToken;
      phoneInput.readOnly = true;
      codeRow.hidden = true;
      sendBtn.hidden = true;
      setHint("휴대폰 인증을 마쳤어요.", "good");
    } catch (err) {
      setHint(err.message, "bad");
      verifyBtn.disabled = false;
    }
  });

  codeInput.addEventListener("keydown", (e) => {
    if (e.key === "Enter") {
      e.preventDefault();
      verifyBtn.click();
    }
  });

  return state;
}

/* ---------- 라우터 ---------- */

function route() {
  const { page, id, sub, params } = parseHash();
  stopNearby();
  setTab(page);
  document.getElementById("app").classList.toggle("wide", page === "admin");
  if (page === "admin") return renderAdmin(id, sub, params);
  if (page === "voc" && id === "new") return renderVocForm(params);
  window.scrollTo(0, 0);
  if (page === "food" || page === "spot" || page === "search") return renderList(page, params);
  if (page === "nearby" && id) return renderNearbyDetail(decodeURIComponent(id));
  if (page === "nearby") return renderNearby(params);
  if (page === "place" && id) return renderPlace(id);
  if (page === "companion" && id === "new") return renderCompanionForm();
  if (page === "companion" && id) return renderCompanionDetail(id);
  if (page === "companion") return renderCompanionList(params);
  if (page === "login") return renderLogin(params);
  if (page === "signup") return renderSignup(params);
  if (page === "me") return renderMe();
  if (page === "dev" && id === "sms") return renderDevSms();
  return renderHome();
}

/* ---------- 홈 ---------- */

function renderHome() {
  view.innerHTML = `
    <p class="lead">혼밥도, 혼자 걷는 하루도, 같이 떠날 메이트도. 전부 여기서.</p>
    <h1>혼자라서 더 좋은 길,<br>오늘은 어디로 갈까요?</h1>
    <div class="grid-2">
      <a class="tile" href="#/food">
        <strong>혼밥각</strong>
        <span>눈치 안 보고 먹는 1인석·카운터 맛집</span>
      </a>
      <a class="tile" href="#/spot">
        <strong>혼놀스팟</strong>
        <span>나 혼자 걷고, 찍고, 쉬어가기 좋은 스팟</span>
      </a>
      <a class="tile wide" href="#/nearby">
        <strong>지금 근처</strong>
        <span>내 위치 기준으로 가까운 식당을 바로 찾아 길찾기까지</span>
      </a>
      <a class="tile wide" href="#/companion">
        <strong>같이가요</strong>
        <span>제주 렌터카, 올레길, 한라산까지. 같이 떠날 메이트를 만나 보세요</span>
      </a>
    </div>
    <form class="search" id="home-search">
      <input name="q" placeholder="어디 가볼까요? (성수, 해운대, 제주…)" autocomplete="off">
      <button class="primary" type="submit">검색</button>
    </form>
  `;
  document.getElementById("home-search").addEventListener("submit", (e) => {
    e.preventDefault();
    const q = new FormData(e.target).get("q") || "";
    location.hash = `#/search?q=${encodeURIComponent(q)}`;
  });
}

/* ---------- 혼밥 / 혼장소 목록 ---------- */

async function renderList(page, params) {
  const intent = page === "food" || page === "spot" ? page : (params.get("intent") || "");
  const soloDining = params.get("soloDining") || "";
  const q = params.get("q") || "";
  const title = intent === "food"
    ? "눈치 안 보고<br>혼밥하기 좋은 곳"
    : intent === "spot" ? "혼자 가도<br>완벽한 스팟" : "검색 결과";
  const base = intent ? `#/${intent}` : "#/search";

  view.innerHTML = `
    <h1>${title}</h1>
    <form class="search" id="list-search">
      <input name="q" value="${escapeHtml(q)}" placeholder="지역이나 장소 이름">
      <button class="primary" type="submit">찾기</button>
    </form>
    <div class="filters">
      <a class="chip ${soloDining === "YES" ? "on" : ""}" href="${base}?q=${encodeURIComponent(q)}&soloDining=YES">혼밥 OK</a>
      <a class="chip ${soloDining === "COUNTER" ? "on" : ""}" href="${base}?q=${encodeURIComponent(q)}&soloDining=COUNTER">카운터·1인석</a>
      ${soloDining ? `<a class="chip" href="${base}?q=${encodeURIComponent(q)}">필터 해제</a>` : ""}
    </div>
    <div id="results" class="empty">불러오는 중이에요…</div>
  `;

  document.getElementById("list-search").addEventListener("submit", (e) => {
    e.preventDefault();
    const next = new URLSearchParams();
    const query = new FormData(e.target).get("q");
    if (query) next.set("q", query);
    if (soloDining) next.set("soloDining", soloDining);
    location.hash = `${base}?${next}`;
  });

  const query = new URLSearchParams();
  if (intent) query.set("intent", intent);
  if (q) query.set("q", q);
  if (soloDining) query.set("soloDining", soloDining);

  const results = document.getElementById("results");
  try {
    const places = await api(`/api/places?${query}`);
    if (!places.length) {
      results.textContent = "아직 딱 맞는 곳이 없어요. 다른 키워드로 찾아볼까요?";
      return;
    }
    results.className = "";
    results.innerHTML = places.map((p) => `
      <a class="card" href="#/place/${p.id}">
        <p class="meta">${CATEGORY[p.category]} · ${escapeHtml(p.area)} · 솔로 지수 ${p.soloScore}/5</p>
        <h3>${escapeHtml(p.name)}</h3>
        <p class="lead">${escapeHtml(p.summary)}</p>
        ${badge(p.soloDining)}
        <span class="meta">${p.reviewCount ? `후기 ${p.reviewCount}개 · ★ ${p.averageRating}` : "첫 후기를 기다려요"}</span>
        <div class="tags">${tagList(p.tags)}</div>
      </a>
    `).join("");
  } catch {
    results.className = "error";
    results.textContent = "목록을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.";
  }
}

/* ---------- 내주변 식당 ---------- */

const NEARBY_RADII = [100, 300, 1000];
const REFETCH_DISTANCE_M = 200;
const LEAFLET_URL = "https://unpkg.com/leaflet@1.9.4/dist";
const NAVER_MAP_CLIENT_ID = "n3fwh7yi5i";
const NEARBY_CACHE_KEY = "wayalone.nearby";

const nearby = {
  session: 0,
  watchId: null,
  map: null,
  range: null,
  meMarker: null,
  current: null,
  origin: null,
  spot: null,
  radius: 1000,
  page: 1,
  places: [],
  markers: [],
};

let naverPromise = null;
let naverFailed = false;

/** 네이버 지도 콘솔에 등록되지 않은 주소에서는 인증이 실패하므로, 그때는 OpenStreetMap 으로 다시 그립니다. */
function loadNaverMaps() {
  if (naverFailed) return Promise.reject(new Error("naver auth failed"));
  if (window.naver && window.naver.maps) return Promise.resolve(window.naver.maps);
  if (!naverPromise) {
    naverPromise = new Promise((resolve, reject) => {
      window.navermap_authFailure = () => {
        naverFailed = true;
        if (location.hash.startsWith("#/nearby")) route();
      };
      const script = document.createElement("script");
      script.src = `https://oapi.map.naver.com/openapi/v3/maps.js?ncpKeyId=${NAVER_MAP_CLIENT_ID}`;
      script.onload = () => (window.naver && window.naver.maps ? resolve(window.naver.maps) : reject(new Error("naver missing")));
      script.onerror = () => {
        naverFailed = true;
        reject(new Error("naver load failed"));
      };
      document.head.appendChild(script);
    });
  }
  return naverPromise;
}

async function loadMapEngine() {
  try {
    await loadNaverMaps();
    return naverEngine;
  } catch {
    await loadLeaflet();
    return leafletEngine;
  }
}

const MAP_ACCENT = "#c0582a";

/** 화면 코드는 이 공통 인터페이스만 씁니다: marker / circle / polyline / fit / focus / destroy */
const naverEngine = {
  create(el, center, zoom, { scrollZoom = true } = {}) {
    const N = window.naver.maps;
    const ll = ([lat, lng]) => new N.LatLng(lat, lng);
    const map = new N.Map(el, {
      center: ll(center),
      zoom,
      scrollWheel: scrollZoom,
      zoomControl: true,
      zoomControlOptions: { position: N.Position.TOP_RIGHT, style: N.ZoomControlStyle.SMALL },
      mapDataControl: false,
      scaleControl: false,
    });
    const info = new N.InfoWindow({
      content: "",
      borderWidth: 0,
      backgroundColor: "transparent",
      disableAnchor: true,
      pixelOffset: new N.Point(0, -8),
    });
    return {
      marker(pos, { kind = "place", popup, onClick } = {}) {
        const icons = {
          me: { content: '<div class="me-dot"><span></span></div>', anchor: new N.Point(11, 11) },
          spot: { content: '<div class="spot-dot"><span></span></div>', anchor: new N.Point(12, 12) },
          place: { content: '<div class="pin"><span></span></div>', anchor: new N.Point(13, 32) },
        };
        const marker = new N.Marker({
          position: ll(pos), map, icon: icons[kind] || icons.place, zIndex: kind === "place" ? 100 : 200,
        });
        const open = () => {
          if (!popup) return;
          info.setContent(`<div class="map-popup">${popup}</div>`);
          info.open(map, marker);
        };
        N.Event.addListener(marker, "click", () => {
          open();
          if (onClick) onClick();
        });
        return { setPosition: (p) => marker.setPosition(ll(p)), open, remove: () => marker.setMap(null) };
      },
      circle(c, radius) {
        const circle = new N.Circle({
          map, center: ll(c), radius,
          strokeColor: MAP_ACCENT, strokeWeight: 1, strokeOpacity: 0.7, fillColor: MAP_ACCENT, fillOpacity: 0.05,
        });
        return { setCenter: (p) => circle.setCenter(ll(p)) };
      },
      polyline(points) {
        new N.Polyline({
          map, path: points.map(ll), strokeColor: MAP_ACCENT, strokeWeight: 3, strokeOpacity: 0.8, strokeStyle: "shortdash",
        });
      },
      fit(points, padding = 40, maxZoom = 17) {
        const bounds = new N.LatLngBounds(ll(points[0]), ll(points[0]));
        points.forEach((p) => bounds.extend(ll(p)));
        map.fitBounds(bounds, { top: padding, right: padding, bottom: padding, left: padding });
        if (map.getZoom() > maxZoom) map.setZoom(maxZoom);
      },
      focus(pos, minZoom) {
        map.setCenter(ll(pos));
        if (map.getZoom() < minZoom) map.setZoom(minZoom);
      },
      destroy() {
        // 인증 실패 후에는 SDK가 이미 내부를 정리해서 close/destroy 가 예외를 던질 수 있습니다.
        // 그대로 두면 OpenStreetMap 으로 다시 그리는 단계까지 가지 못합니다.
        try { info.close(); } catch { /* ignore */ }
        try { map.destroy(); } catch { /* ignore */ }
      },
    };
  },
};

const leafletEngine = {
  create(el, center, zoom, { scrollZoom = true } = {}) {
    const L = window.L;
    const map = L.map(el, { scrollWheelZoom: scrollZoom }).setView(center, zoom);
    addBaseTiles(map);
    return {
      marker(pos, { kind = "place", popup, onClick } = {}) {
        const icon = { me: meDot, spot: spotDot }[kind] || placePin;
        const marker = L.marker(pos, { icon: icon(), zIndexOffset: kind === "place" ? 0 : 1000 }).addTo(map);
        if (popup) marker.bindPopup(popup);
        if (onClick) marker.on("click", onClick);
        return { setPosition: (p) => marker.setLatLng(p), open: () => popup && marker.openPopup(), remove: () => marker.remove() };
      },
      circle(c, radius) {
        const circle = L.circle(c, { radius, color: MAP_ACCENT, weight: 1, fillOpacity: 0.05 }).addTo(map);
        return { setCenter: (p) => circle.setLatLng(p) };
      },
      polyline(points) {
        L.polyline(points, { color: MAP_ACCENT, weight: 3, dashArray: "6 8", opacity: 0.8 }).addTo(map);
      },
      fit(points, padding = 40, maxZoom = 17) {
        map.fitBounds(points, { padding: [padding, padding], maxZoom });
      },
      focus(pos, minZoom) {
        map.setView(pos, Math.max(map.getZoom(), minZoom));
      },
      destroy() {
        map.remove();
      },
    };
  },
};

let leafletPromise = null;

function loadLeaflet() {
  if (window.L) return Promise.resolve(window.L);
  if (!leafletPromise) {
    leafletPromise = new Promise((resolve, reject) => {
      const css = document.createElement("link");
      css.rel = "stylesheet";
      css.href = `${LEAFLET_URL}/leaflet.css`;
      document.head.appendChild(css);
      const script = document.createElement("script");
      script.src = `${LEAFLET_URL}/leaflet.js`;
      script.onload = () => resolve(window.L);
      script.onerror = () => {
        leafletPromise = null;
        script.remove();
        reject(new Error("지도를 불러오지 못했어요. 목록으로 보여 드릴게요."));
      };
      document.head.appendChild(script);
    });
  }
  return leafletPromise;
}

function stopNearby() {
  nearby.session += 1;
  if (nearby.watchId != null) navigator.geolocation.clearWatch(nearby.watchId);
  if (nearby.map) nearby.map.destroy();
  Object.assign(nearby, {
    watchId: null, map: null, range: null, meMarker: null,
    current: null, origin: null, spot: null, page: 1, places: [], markers: [],
  });
}

function distanceMeters([lat1, lng1], [lat2, lng2]) {
  const rad = (d) => (d * Math.PI) / 180;
  const a = Math.sin(rad(lat2 - lat1) / 2) ** 2
    + Math.cos(rad(lat1)) * Math.cos(rad(lat2)) * Math.sin(rad(lng2 - lng1) / 2) ** 2;
  return 6371000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

function formatDistance(m) {
  return m < 1000 ? `${Math.round(m)}m` : `${(m / 1000).toFixed(1).replace(/\.0$/, "")}km`;
}

/** 카카오맵 링크 형식은 쉼표로 값을 구분하므로 이름에서 쉼표를 뺍니다. */
function kakaoLinkName(name) {
  return encodeURIComponent(String(name).replaceAll(",", " "));
}

function directionsUrl(place, from = nearby.current) {
  const to = `${kakaoLinkName(place.name)},${place.latitude},${place.longitude}`;
  if (!from) return `https://map.kakao.com/link/to/${to}`;
  const [lat, lng] = from;
  return `https://map.kakao.com/link/from/${kakaoLinkName("내 위치")},${lat},${lng}/to/${to}`;
}

/** 네이버지도 웹에서 식당을 검색해 열어 줍니다. 거기서 바로 길찾기를 누를 수 있어요. */
function naverMapUrl(place) {
  return `https://map.naver.com/p/search/${encodeURIComponent(place.name)}`;
}

/** 네이버지도 길찾기 링크 속성. 클릭 처리는 아래 openNaverRoute 가 맡습니다. */
function naverRouteAttrs(place, from) {
  return [
    `href="${escapeHtml(naverMapUrl(place))}" target="_blank" rel="noopener" data-naver-route`,
    `data-lat="${place.latitude}" data-lng="${place.longitude}" data-name="${escapeHtml(place.name)}"`,
    from ? `data-from="${from[0]},${from[1]}"` : "",
  ].join(" ");
}

const IS_MOBILE = /Android|iPhone|iPad|iPod/i.test(navigator.userAgent);

/** 휴대폰에서는 네이버지도 앱으로 도보 길찾기를 열고, 앱이 없으면 웹으로 넘어갑니다. PC는 웹 링크 그대로. */
function openNaverRoute(link, event) {
  if (!IS_MOBILE) return;
  event.preventDefault();
  const query = new URLSearchParams();
  if (link.dataset.from) {
    const [slat, slng] = link.dataset.from.split(",");
    query.set("slat", slat);
    query.set("slng", slng);
    query.set("sname", "내 위치");
  }
  query.set("dlat", link.dataset.lat);
  query.set("dlng", link.dataset.lng);
  query.set("dname", link.dataset.name);
  query.set("appname", location.hostname);
  const fallback = setTimeout(() => { location.href = link.href; }, 1500);
  const cancel = () => clearTimeout(fallback);
  window.addEventListener("pagehide", cancel, { once: true });
  document.addEventListener("visibilitychange", () => { if (document.hidden) cancel(); }, { once: true });
  location.href = `nmap://route/walk?${query}`;
}

document.addEventListener("click", (e) => {
  const link = e.target.closest("a[data-naver-route]");
  if (link) openNaverRoute(link, e);
});

function safeExternalUrl(url) {
  const secure = String(url || "").replace(/^http:\/\//, "https://");
  return /^https:\/\//.test(secure) ? secure : "";
}

function phoneDigits(phone) {
  return (phone || "").replace(/[^\d-]/g, "");
}

function walkMinutes(distance) {
  return Math.max(1, Math.round(distance / 70));
}

/** 상세 화면에서 다시 꺼내 쓸 수 있도록 마지막으로 본 주변 식당을 탭 단위로 기억합니다. */
function rememberNearby(places) {
  let saved = {};
  try {
    saved = JSON.parse(sessionStorage.getItem(NEARBY_CACHE_KEY)) || {};
  } catch {
    saved = {};
  }
  const byId = saved.places || {};
  const fromLabel = nearby.spot ? nearby.spot.label : null;
  places.forEach((p) => { byId[p.kakaoId] = { ...p, fromLabel }; });
  sessionStorage.setItem(NEARBY_CACHE_KEY, JSON.stringify({ places: byId, current: nearby.current }));
}

function recalledNearby() {
  try {
    return JSON.parse(sessionStorage.getItem(NEARBY_CACHE_KEY)) || {};
  } catch {
    return {};
  }
}

function addBaseTiles(map) {
  window.L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
    maxZoom: 19,
    attribution: "&copy; OpenStreetMap",
  }).addTo(map);
}

function placePin() {
  return window.L.divIcon({ className: "pin", html: "<span></span>", iconSize: [26, 34], iconAnchor: [13, 32], popupAnchor: [0, -28] });
}

function meDot() {
  return window.L.divIcon({ className: "me-dot", html: "<span></span>", iconSize: [22, 22], iconAnchor: [11, 11] });
}

function spotDot() {
  return window.L.divIcon({ className: "spot-dot", html: "<span></span>", iconSize: [24, 24], iconAnchor: [12, 12] });
}

function nearbyCard(place, index) {
  const phone = phoneDigits(place.phone);
  return `
    <article class="card nearby-card" data-index="${index}" data-id="${escapeHtml(place.kakaoId)}">
      <p class="meta">${escapeHtml(place.category || "음식점")} · ${formatDistance(place.distance)} · 도보 ${walkMinutes(place.distance)}분</p>
      <h3>${escapeHtml(place.name)}</h3>
      <p class="meta">${escapeHtml(place.address)}</p>
      <div class="actions">
        <a class="naver-btn" ${naverRouteAttrs(place, nearby.current)}>네이버지도 길찾기</a>
        ${phone ? `<a class="ghost" href="tel:${phone}">전화</a>` : ""}
        <a class="ghost" href="#/nearby/${encodeURIComponent(place.kakaoId)}">상세 보기</a>
      </div>
    </article>
  `;
}

function highlightNearby(index) {
  document.querySelectorAll(".nearby-card").forEach((el) => {
    const on = Number(el.dataset.index) === index;
    el.classList.toggle("focus", on);
    if (on) el.scrollIntoView({ behavior: "smooth", block: "nearest" });
  });
}

function addNearbyMarkers(places, offset) {
  if (!nearby.map) return;
  places.forEach((place, i) => {
    nearby.markers[offset + i] = nearby.map.marker([place.latitude, place.longitude], {
      popup: `
        <strong>${escapeHtml(place.name)}</strong><br>
        <span>${escapeHtml(place.category || "")} · ${formatDistance(place.distance)}</span><br>
        <a href="#/nearby/${encodeURIComponent(place.kakaoId)}">상세 보기</a>
      `,
      onClick: () => highlightNearby(offset + i),
    });
  });
}

function fitNearby() {
  const center = nearby.origin || nearby.current;
  if (!nearby.map || !center) return;
  nearby.map.fit([center, ...nearby.places.map((p) => [p.latitude, p.longitude])], 40, 17);
}

async function loadNearbyPlaces(page, session) {
  const list = document.getElementById("nearby-list");
  const more = document.getElementById("nearby-more");
  const status = document.getElementById("nearby-status");
  const [lat, lng] = nearby.origin;
  more.disabled = true;
  try {
    const query = new URLSearchParams({ lat, lng, radius: nearby.radius, page });
    const res = await api(`/api/places/nearby?${query}`);
    if (session !== nearby.session) return;
    if (page === 1) {
      nearby.places = [];
      nearby.markers.forEach((m) => m.remove());
      nearby.markers = [];
      list.innerHTML = "";
    }
    const offset = nearby.places.length;
    nearby.places.push(...res.places);
    nearby.page = page;
    rememberNearby(res.places);
    if (!nearby.places.length) {
      list.innerHTML = `<p class="empty">반경 ${formatDistance(nearby.radius)} 안에 식당이 없어요. 범위를 넓혀 볼까요?</p>`;
    } else {
      list.insertAdjacentHTML("beforeend", res.places.map((p, i) => nearbyCard(p, offset + i)).join(""));
      addNearbyMarkers(res.places, offset);
      if (page === 1) fitNearby();
    }
    more.hidden = res.end;
  } catch (err) {
    if (session !== nearby.session) return;
    status.className = "hint bad";
    status.textContent = err.message;
  }
  more.disabled = false;
}

function locationErrorMessage(err) {
  if (err.code === 1) return "위치 권한이 꺼져 있어요. 브라우저 설정에서 이 사이트의 위치 권한을 허용해 주세요.";
  if (err.code === 3) return "위치를 확인하는 데 시간이 오래 걸려요. 잠시 후 다시 시도해 주세요.";
  return "현재 위치를 확인하지 못했어요. GPS나 네트워크 상태를 확인해 주세요.";
}

const POPULAR_SPOTS = ["성수역", "홍대입구역", "강남역", "을지로3가역", "해운대역"];

/** 반경·기준 위치를 유지한 채 주소(hash)를 만듭니다. spot 이 없으면 내 위치 기준입니다. */
function nearbyHash(radius, spot) {
  const query = new URLSearchParams({ radius });
  if (spot) {
    query.set("lat", spot.at[0].toFixed(6));
    query.set("lng", spot.at[1].toFixed(6));
    query.set("label", spot.label);
  }
  return `#/nearby?${query}`;
}

function parseSpot(params) {
  const lat = Number.parseFloat(params.get("lat"));
  const lng = Number.parseFloat(params.get("lng"));
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) return null;
  return { at: [lat, lng], label: (params.get("label") || "선택한 위치").slice(0, 40) };
}

async function renderNearby(params) {
  const session = nearby.session;
  const requested = Number(params.get("radius"));
  nearby.radius = NEARBY_RADII.includes(requested) ? requested : 1000;
  const spot = parseSpot(params);
  nearby.spot = spot;

  view.innerHTML = `
    <h1>${spot ? `${escapeHtml(spot.label)} 주변<br>혼밥하기 좋은 식당` : "지금 내 주변<br>혼밥하기 좋은 식당"}</h1>
    <p class="lead">${spot ? "고른 위치를 기준으로 가까운 식당을 보여 줘요." : "실시간 위치를 따라 가까운 식당을 보여 줘요."} 길찾기를 누르면 네이버지도가 안내해요.</p>
    <div class="filters">
      ${NEARBY_RADII.map((r) => `<a class="chip ${r === nearby.radius ? "on" : ""}" href="${escapeHtml(nearbyHash(r, spot))}">${formatDistance(r)}</a>`).join("")}
      ${spot
        ? `<a class="chip" href="${escapeHtml(nearbyHash(nearby.radius, null))}">내 위치로</a>`
        : `<button class="chip" type="button" id="nearby-recenter">내 위치로</button>`}
    </div>
    <form class="search spot-search" id="spot-search" autocomplete="off">
      <input name="q" maxlength="50" placeholder="다른 동네 보기 (예: 성수역, 망원동)" value="${spot ? escapeHtml(spot.label) : ""}">
      <button class="primary" type="submit">찾기</button>
    </form>
    <div class="suggest" id="spot-suggest" hidden></div>
    <div class="filters spot-quick">
      ${POPULAR_SPOTS.map((name) => `<button class="chip ${spot && spot.label === name ? "on" : ""}" type="button" data-spot="${name}">${name}</button>`).join("")}
    </div>
    <div id="nearby-map" class="map"></div>
    <p class="hint" id="nearby-status">${spot ? "식당을 찾는 중이에요…" : "위치를 확인하는 중이에요…"}</p>
    <div id="nearby-list"></div>
    <button class="ghost block" type="button" id="nearby-more" hidden>더 보기</button>
  `;

  const status = document.getElementById("nearby-status");
  const mapEl = document.getElementById("nearby-map");
  const suggest = document.getElementById("spot-suggest");
  const canLocate = window.isSecureContext && "geolocation" in navigator;

  const goSpot = (place) => {
    location.hash = nearbyHash(nearby.radius, { at: [place.latitude, place.longitude], label: place.name });
  };
  const searchSpot = async (query, pickFirst) => {
    suggest.hidden = true;
    try {
      const results = await api(`/api/places/locations?q=${encodeURIComponent(query)}`);
      if (session !== nearby.session) return;
      if (!results.length) {
        suggest.hidden = false;
        suggest.innerHTML = `<p class="empty">"${escapeHtml(query)}" 위치를 찾지 못했어요.</p>`;
        return;
      }
      if (pickFirst) {
        goSpot({ ...results[0], name: query });
        return;
      }
      suggest.hidden = false;
      suggest.innerHTML = results.map((r, i) => `
        <button class="suggest-item" type="button" data-index="${i}">
          <strong>${escapeHtml(r.name)}</strong><span>${escapeHtml(r.address)}</span>
        </button>
      `).join("");
      suggest.onclick = (e) => {
        const item = e.target.closest(".suggest-item");
        if (item) goSpot(results[Number(item.dataset.index)]);
      };
    } catch (err) {
      suggest.hidden = false;
      suggest.innerHTML = `<p class="error inline">${escapeHtml(err.message)}</p>`;
    }
  };

  document.getElementById("spot-search").addEventListener("submit", (e) => {
    e.preventDefault();
    const query = new FormData(e.target).get("q").trim();
    if (query) searchSpot(query, false);
  });
  document.querySelector(".spot-quick").addEventListener("click", (e) => {
    const chip = e.target.closest("[data-spot]");
    if (chip) searchSpot(chip.dataset.spot, true);
  });
  document.getElementById("nearby-more").addEventListener("click", () => {
    loadNearbyPlaces(nearby.page + 1, session);
  });
  const recenter = document.getElementById("nearby-recenter");
  if (recenter) recenter.addEventListener("click", fitNearby);
  document.getElementById("nearby-list").addEventListener("click", (e) => {
    if (e.target.closest("a")) return;
    const card = e.target.closest(".nearby-card");
    if (card) location.hash = `#/nearby/${encodeURIComponent(card.dataset.id)}`;
  });

  if (!spot && !canLocate) {
    mapEl.hidden = true;
    status.className = "hint bad";
    status.textContent = "이 주소에서는 내 위치를 쓸 수 없어요. 위에서 동네를 검색해 보세요.";
    return;
  }

  let engine = null;
  try {
    engine = await loadMapEngine();
  } catch {
    mapEl.hidden = true;
    status.textContent = "지도를 불러오지 못했어요. 목록으로 보여 드릴게요.";
  }
  if (session !== nearby.session) return;

  if (spot) {
    nearby.origin = spot.at;
    if (engine) {
      nearby.map = engine.create(mapEl, spot.at, 15);
      nearby.range = nearby.map.circle(spot.at, nearby.radius);
      nearby.map.marker(spot.at, { kind: "spot", popup: `<strong>${escapeHtml(spot.label)}</strong>` });
    }
    status.className = "hint";
    status.textContent = `${spot.label} 기준 · 반경 ${formatDistance(nearby.radius)}`;
    loadNearbyPlaces(1, session);
  }
  if (!canLocate) return;

  const onPosition = (pos) => {
    if (session !== nearby.session) return;
    const { latitude, longitude, accuracy } = pos.coords;
    const here = [latitude, longitude];
    nearby.current = here;

    if (spot) {
      if (nearby.map && !nearby.meMarker) {
        nearby.meMarker = nearby.map.marker(here, { kind: "me", popup: "<strong>내 위치</strong>" });
      } else if (nearby.meMarker) {
        nearby.meMarker.setPosition(here);
      }
      return;
    }

    if (engine && !nearby.map) {
      nearby.map = engine.create(mapEl, here, 15);
      nearby.range = nearby.map.circle(here, nearby.radius);
      nearby.meMarker = nearby.map.marker(here, { kind: "me", popup: "<strong>내 위치</strong>" });
    } else if (nearby.map) {
      nearby.meMarker.setPosition(here);
      nearby.range.setCenter(here);
    }

    status.className = "hint";
    status.textContent = `실시간 위치 기준 · 정확도 약 ${Math.round(accuracy)}m`;

    const refetchDistance = Math.min(REFETCH_DISTANCE_M, nearby.radius / 2);
    if (!nearby.origin || distanceMeters(nearby.origin, here) > refetchDistance) {
      nearby.origin = here;
      loadNearbyPlaces(1, session);
    }
  };

  const onError = (err) => {
    if (session !== nearby.session || nearby.current || spot) return;
    status.className = "hint bad";
    status.textContent = `${locationErrorMessage(err)} 위에서 동네를 검색해서 볼 수도 있어요.`;
  };

  nearby.watchId = navigator.geolocation.watchPosition(onPosition, onError, {
    enableHighAccuracy: true,
    maximumAge: 10000,
    timeout: 15000,
  });
}

async function renderNearbyDetail(kakaoId) {
  const session = nearby.session;
  const saved = recalledNearby();
  const place = (saved.places || {})[kakaoId];
  if (!place) {
    view.innerHTML = `
      <p class="empty">식당 정보를 찾지 못했어요. 주변 목록에서 다시 골라 주세요.</p>
      <a class="primary block" href="#/nearby">주변 식당 보기</a>
    `;
    return;
  }

  const from = saved.current || null;
  const phone = phoneDigits(place.phone);
  const kakaoUrl = safeExternalUrl(place.kakaoUrl);
  view.innerHTML = `
    <a class="back" href="#/nearby">← 주변 식당</a>
    <p class="meta">${escapeHtml(place.category || "음식점")}</p>
    <h1>${escapeHtml(place.name)}</h1>
    <p class="lead">${place.fromLabel ? `${escapeHtml(place.fromLabel)}에서` : "내 위치에서"} ${formatDistance(place.distance)} · 걸어서 약 ${walkMinutes(place.distance)}분</p>
    <div id="detail-map" class="map map-sm"></div>
    <div class="info">
      <div><span class="k">주소</span><span>${escapeHtml(place.address || "정보 없음")}</span></div>
      <div><span class="k">전화</span><span>${phone ? `<a href="tel:${phone}">${escapeHtml(place.phone)}</a>` : "정보 없음"}</span></div>
      <div><span class="k">분류</span><span>${escapeHtml(place.category || "음식점")}</span></div>
    </div>
    <div class="actions">
      <a class="naver-btn" ${naverRouteAttrs(place, from)}>네이버지도 길찾기</a>
      <a class="ghost" href="${escapeHtml(directionsUrl(place, from))}" target="_blank" rel="noopener">카카오지도 길찾기</a>
      ${phone ? `<a class="ghost" href="tel:${phone}">전화</a>` : ""}
    </div>
    <a class="naver-outline block narrow-top detail-link" href="${escapeHtml(naverMapUrl(place))}" target="_blank" rel="noopener">네이버지도에서 사진·리뷰 보기</a>
    ${kakaoUrl ? `<a class="ghost block narrow-top detail-link" href="${escapeHtml(kakaoUrl)}" target="_blank" rel="noopener">카카오지도에서 메뉴·사진·리뷰 보기</a>` : ""}
  `;

  document.querySelector(".back").addEventListener("click", (e) => {
    if (history.length > 1) {
      e.preventDefault();
      history.back();
    }
  });

  const mapEl = document.getElementById("detail-map");
  let engine;
  try {
    engine = await loadMapEngine();
  } catch {
    mapEl.hidden = true;
    return;
  }
  if (session !== nearby.session) return;

  const spot = [place.latitude, place.longitude];
  nearby.map = engine.create(mapEl, spot, 17, { scrollZoom: false });
  nearby.map.marker(spot, { popup: `<strong>${escapeHtml(place.name)}</strong>` });
  if (from) {
    nearby.map.marker(from, { kind: "me", popup: "<strong>내 위치</strong>" });
    nearby.map.polyline([from, spot]);
    nearby.map.fit([from, spot], 36, 17);
  }
}

/* ---------- 장소 상세 ---------- */

async function renderPlace(id) {
  view.innerHTML = `<p class="empty">불러오는 중이에요…</p>`;
  try {
    const [place, reviews] = await Promise.all([
      api(`/api/places/${id}`),
      api(`/api/places/${id}/reviews`),
    ]);
    view.innerHTML = `
      <p class="meta">${CATEGORY[place.category]} · ${escapeHtml(place.area)}</p>
      <h1>${escapeHtml(place.name)}</h1>
      <p class="lead">${escapeHtml(place.summary)}</p>
      <p class="meta">${escapeHtml(place.address || "")}</p>
      <p>${badge(place.soloDining)} <span class="meta">솔로 지수 ${place.soloScore}/5</span></p>
      <div class="tags">${tagList(place.tags)}</div>
      <h2>혼자 다녀온 사람들의 솔직 후기</h2>
      <div id="reviews">
        ${reviews.length ? reviews.map(reviewCard).join("") : `<p class="empty">아직 후기가 없어요. 첫 후기를 남겨 주세요!</p>`}
      </div>
      ${me ? `
      <form class="form" id="review-form">
        <p class="hint">${escapeHtml(me.nickname)}님 이름으로 남겨져요.</p>
        <label class="field"><span>별점</span>
          <select name="rating">
            <option value="5">★★★★★ 최고예요</option>
            <option value="4">★★★★ 좋아요</option>
            <option value="3">★★★ 괜찮아요</option>
            <option value="2">★★ 아쉬워요</option>
            <option value="1">★ 별로예요</option>
          </select>
        </label>
        <textarea name="content" rows="3" maxlength="1000" placeholder="혼자 갔을 때 어땠어요? 1인석, 웨이팅, 분위기를 알려 주세요." required></textarea>
        <label class="check"><input type="checkbox" name="wentAlone" checked> 나 혼자 다녀왔어요</label>
        <p class="error inline" id="review-error" hidden></p>
        <button class="primary" type="submit">후기 남기기</button>
      </form>` : loginPrompt("로그인하면 후기를 남길 수 있어요.")}
    `;
    const form = document.getElementById("review-form");
    if (form) {
      form.addEventListener("submit", async (e) => {
        e.preventDefault();
        const error = document.getElementById("review-error");
        const button = form.querySelector("button[type=submit]");
        error.hidden = true;
        button.disabled = true;
        try {
          const data = new FormData(form);
          await api(`/api/places/${id}/reviews`, {
            method: "POST",
            body: JSON.stringify({
              rating: Number(data.get("rating")),
              content: data.get("content"),
              wentAlone: data.get("wentAlone") === "on",
            }),
          });
          await renderPlace(id);
        } catch (err) {
          if (handleAuthError(err)) return;
          showError(error, err.message);
          button.disabled = false;
        }
      });
    }
  } catch {
    view.innerHTML = `<p class="error">장소를 찾지 못했어요.</p>`;
  }
}

function reviewCard(r) {
  return `
    <article class="review">
      <p class="meta">${escapeHtml(r.nickname)} · ★ ${r.rating}${r.wentAlone ? " · 혼자 다녀옴" : ""}</p>
      <p>${escapeHtml(r.content)}</p>
    </article>
  `;
}

/* ---------- 동행 ---------- */

const QUICK_DESTINATIONS = ["제주", "부산", "서울"];

function statusBadge(post) {
  return post.status === "ENDED"
    ? `<span class="badge ended">마감</span>`
    : `<span class="badge open">모집 중</span>`;
}

/** 목록 카드. relation: "mine"(내 모집글) | 신청 상태("PENDING" 등) | null */
function companionCard(p, relation) {
  const relationBadge = relation === "mine"
    ? `<span class="badge mine">내 모집글</span>`
    : relation ? `<span class="badge ${APPLICATION_STATUS[relation].cls}">내 신청 · ${APPLICATION_STATUS[relation].text}</span>` : "";
  return `
    <a class="card ${relation === "mine" ? "card-mine" : ""}" href="#/companion/${p.id}">
      <p class="meta">${statusBadge(p)} ${relationBadge} ${p.hidden ? `<span class="badge no">운영자 숨김</span>` : ""} ${escapeHtml(p.destination)} · ${dateRange(p)}</p>
      <h3>${escapeHtml(p.title)}</h3>
      <p class="meta">${escapeHtml(p.nickname)} · ${p.acceptedCount}/${p.recruitCount}명 확정 · 합류 신청 ${p.applicantCount}명</p>
      ${p.style ? `<div class="tags">${tagList(p.style.split("·"))}</div>` : ""}
    </a>
  `;
}

async function renderCompanionList(params) {
  const q = params.get("q") || "";
  const scope = me ? params.get("scope") || "" : "";
  view.innerHTML = `
    <h1>같이 밥 먹고<br>같이 떠날 메이트</h1>
    <p class="lead">혼자 가기 애매한 맛집이나 여행 일정을 올리거나, 끌리는 모임에 합류해 보세요.</p>
    <div class="fab-row">
      <a class="primary block" href="#/companion/new">모집글 올리기</a>
    </div>
    ${me ? `
      <div class="segmented">
        <a class="${scope === "" ? "on" : ""}" href="#/companion">전체</a>
        <a class="${scope === "posts" ? "on" : ""}" href="#/companion?scope=posts">내 모집글</a>
        <a class="${scope === "applied" ? "on" : ""}" href="#/companion?scope=applied">내 신청</a>
      </div>` : ""}
    ${scope ? "" : `
      <form class="search" id="companion-search">
        <input name="q" value="${escapeHtml(q)}" placeholder="어디서 만날까요? (예: 을지로, 제주)">
        <button class="primary" type="submit">찾기</button>
      </form>
      <div class="filters">
        <a class="chip ${q === "" ? "on" : ""}" href="#/companion">전체</a>
        ${QUICK_DESTINATIONS.map((d) => `
          <a class="chip ${q === d ? "on" : ""}" href="#/companion?q=${encodeURIComponent(d)}">${d}</a>
        `).join("")}
      </div>`}
    <div id="results" class="empty">불러오는 중이에요…</div>
  `;
  const search = document.getElementById("companion-search");
  if (search) {
    search.addEventListener("submit", (e) => {
      e.preventDefault();
      const value = new FormData(e.target).get("q") || "";
      location.hash = value ? `#/companion?q=${encodeURIComponent(value)}` : "#/companion";
    });
  }

  const results = document.getElementById("results");
  try {
    const query = new URLSearchParams();
    if (q) query.set("q", q);
    const [posts, mine] = await Promise.all([
      scope ? [] : api(`/api/companions?${query}`),
      me ? api("/api/companions/mine").catch(() => ({ posts: [], applications: [] })) : { posts: [], applications: [] },
    ]);
    const appliedStatus = new Map(mine.applications.map((a) => [a.post.id, a.status]));
    const relationOf = (p) => (me && p.memberId === me.id ? "mine" : appliedStatus.get(p.id) || null);

    let html;
    if (scope === "posts") {
      html = mine.posts.length
        ? mine.posts.map((p) => companionCard(p, "mine")).join("")
        : "아직 올린 모집글이 없어요. 첫 모임을 열어 볼까요?";
    } else if (scope === "applied") {
      html = mine.applications.length
        ? mine.applications.map((a) => companionCard(a.post, a.status)).join("")
        : "아직 신청한 모임이 없어요.";
    } else {
      html = posts.length
        ? posts.map((p) => companionCard(p, relationOf(p))).join("")
        : "아직 올라온 모집글이 없어요. 첫 글의 주인공이 되어 보세요!";
    }
    const isList = html.trim().startsWith("<");
    results.className = isList ? "" : "empty";
    results[isList ? "innerHTML" : "textContent"] = html;
  } catch {
    results.className = "error";
    results.textContent = "목록을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.";
  }
}

async function renderCompanionDetail(id) {
  view.innerHTML = `<p class="empty">불러오는 중이에요…</p>`;
  let post;
  try {
    post = await api(`/api/companions/${id}`);
  } catch {
    view.innerHTML = `<p class="error">게시글을 찾지 못했어요.</p>`;
    return;
  }
  const isAuthor = Boolean(me && post.memberId === me.id);
  const started = post.startDate <= todayIso();
  const [applications, author, targets] = await Promise.all([
    me ? api(`/api/companions/${id}/applications`).catch(() => []) : [],
    post.memberId ? api(`/api/members/${post.memberId}/profile`).catch(() => null) : null,
    me && started ? api(`/api/companions/${id}/ratings/targets`).catch(() => []) : [],
  ]);
  const ended = post.status === "ENDED";
  const mine = isAuthor ? null : applications[0];

  let applyBlock = "";
  if (ended) {
    applyBlock = `<p class="empty">마감된 일정이에요.</p>`;
  } else if (!me) {
    applyBlock = loginPrompt("로그인하고 합류 신청을 남겨 보세요.");
  } else if (isAuthor || mine) {
    applyBlock = "";
  } else if (!me.phoneVerified) {
    applyBlock = `
      <div class="notice">
        <p>같이가요는 안전을 위해 휴대폰 인증을 마친 회원만 신청할 수 있어요.</p>
        <a class="primary block" href="#/me">휴대폰 인증하러 가기</a>
      </div>`;
  } else {
    applyBlock = `
      <form class="form" id="apply-form">
        <p class="hint">${escapeHtml(me.nickname)}님 이름으로 신청돼요. 신청 내용은 작성자에게만 보여요.</p>
        <textarea name="message" rows="3" maxlength="500" placeholder="간단한 자기소개와 함께하고 싶은 이유를 남겨 주세요." required></textarea>
        <p class="error inline" id="apply-error" hidden></p>
        <button class="primary" type="submit">합류 신청하기</button>
      </form>`;
  }

  let applicationsBlock;
  if (isAuthor) {
    applicationsBlock = `
      <h2>합류 신청 ${applications.length}</h2>
      <p class="error inline" id="decide-error" hidden></p>
      <div id="applications">
        ${applications.length
          ? applications.map((a) => applicationCard(a, !ended, id)).join("")
          : `<p class="empty">아직 신청이 없어요.</p>`}
      </div>`;
  } else if (mine) {
    applicationsBlock = `<h2>내 신청</h2>${applicationCard(mine, false)}`;
  } else {
    applicationsBlock = `
      <h2>합류 신청 ${post.applicantCount}명</h2>
      <p class="hint">신청 내용은 작성자와 신청한 본인만 볼 수 있어요.</p>`;
  }

  view.innerHTML = `
    <p class="meta">${statusBadge(post)} ${isAuthor ? `<span class="badge mine">내 모집글</span>` : ""} ${escapeHtml(post.destination)}</p>
    <h1>${escapeHtml(post.title)}</h1>
    ${isAuthor ? `<div class="mine-banner">내가 올린 모집글이에요. 아래에서 신청을 수락하거나 거절할 수 있어요.</div>` : ""}
    <div class="info">
      <div><span class="k">일정</span><strong>${dateRange(post)}</strong></div>
      <div><span class="k">모집</span><strong>${post.acceptedCount}/${post.recruitCount}명 확정 · 신청 ${post.applicantCount}명</strong></div>
      <div><span class="k">작성자</span><span><strong>${escapeHtml(post.nickname)}</strong> ${author ? verifiedBadge(author.phoneVerified) : ""}</span></div>
      ${author ? `<div><span class="k">매너</span><span>${mannerLine(author.manner)}</span></div>` : ""}
    </div>
    ${post.style ? `<div class="tags">${tagList(post.style.split("·"))}</div>` : ""}
    <h2>이런 모임이에요</h2>
    <p class="post-body">${escapeHtml(post.content)}</p>
    <div class="notice safety">
      <p><strong>안전하게 만나요</strong></p>
      <p>첫 만남은 사람이 많은 공공장소에서, 일정과 동행 정보는 가족이나 친구에게 미리 공유해 주세요. 수락 전에는 연락처를 주고받지 않는 걸 권해요.</p>
    </div>
    ${applicationsBlock}
    ${applyBlock}
    ${targets.length ? ratingSection(targets) : ""}
    ${me && !isAuthor ? `<p class="report-row"><a class="report-link" href="#/voc/new?type=REPORT_POST&post=${post.id}">이 모집글 신고하기</a>${post.memberId ? ` · <button class="report-link linkish" type="button" id="block-author">작성자 차단하기</button>` : ""}</p>` : ""}
    ${isAuthor ? `<button class="ghost block danger-link" type="button" id="delete-post">이 모집글 삭제하기</button>` : ""}
    ${me && me.role === "ADMIN" ? `<button class="ghost block danger-link" type="button" id="admin-hide">관리자: 이 글 숨기기</button>` : ""}
    <div class="fab-row"><a class="ghost block" href="#/companion">← 목록으로</a></div>
  `;

  const blockBtn = document.getElementById("block-author");
  if (blockBtn) {
    blockBtn.addEventListener("click", async () => {
      if (!window.confirm(`${post.nickname}님을 차단할까요?\n서로의 모집글이 보이지 않고 신청할 수 없게 돼요. 마이 페이지에서 언제든 해제할 수 있어요.`)) return;
      try {
        await api(`/api/blocks/${post.memberId}`, { method: "POST" });
        location.hash = "#/companion";
      } catch (err) {
        if (handleAuthError(err)) return;
        window.alert(err.message);
      }
    });
  }

  const deleteBtn = document.getElementById("delete-post");
  if (deleteBtn) {
    deleteBtn.addEventListener("click", async () => {
      if (!window.confirm("이 모집글을 삭제할까요?\n삭제하면 목록에서 사라지고 되돌릴 수 없어요.")) return;
      try {
        await api(`/api/companions/${id}`, { method: "DELETE" });
        location.hash = "#/companion";
      } catch (err) {
        if (handleAuthError(err)) return;
        window.alert(err.message);
      }
    });
  }

  const hideBtn = document.getElementById("admin-hide");
  if (hideBtn) {
    hideBtn.addEventListener("click", async () => {
      const reason = window.prompt("숨기는 이유를 적어 주세요. (활동 로그에 남아요)");
      if (reason === null) return;
      try {
        await api(`/api/admin/companions/${id}/visibility`, {
          method: "PATCH",
          body: JSON.stringify({ hidden: true, reason }),
        });
        location.hash = "#/companion";
      } catch (err) {
        if (handleAuthError(err)) return;
        window.alert(err.message);
      }
    });
  }

  const form = document.getElementById("apply-form");
  if (form) {
    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      const error = document.getElementById("apply-error");
      const button = form.querySelector("button[type=submit]");
      error.hidden = true;
      button.disabled = true;
      try {
        const data = new FormData(form);
        await api(`/api/companions/${id}/applications`, {
          method: "POST",
          body: JSON.stringify({ message: data.get("message") }),
        });
        await renderCompanionDetail(id);
      } catch (err) {
        if (handleAuthError(err)) return;
        showError(error, err.message);
        button.disabled = false;
      }
    });
  }

  const list = document.getElementById("applications");
  if (list) {
    list.addEventListener("click", async (e) => {
      const blockApplicant = e.target.closest("[data-block]");
      if (blockApplicant) {
        if (!window.confirm(`${blockApplicant.dataset.name}님을 차단할까요?\n이 신청은 목록에서 사라지고, 서로의 모집글이 보이지 않게 돼요.`)) return;
        try {
          await api(`/api/blocks/${blockApplicant.dataset.block}`, { method: "POST" });
          await renderCompanionDetail(id);
        } catch (err) {
          if (handleAuthError(err)) return;
          showError(document.getElementById("decide-error"), err.message);
        }
        return;
      }
      const btn = e.target.closest("[data-decide]");
      if (!btn) return;
      btn.disabled = true;
      try {
        await api(`/api/companions/${id}/applications/${btn.dataset.app}`, {
          method: "PATCH",
          body: JSON.stringify({ status: btn.dataset.decide }),
        });
        await renderCompanionDetail(id);
      } catch (err) {
        if (handleAuthError(err)) return;
        showError(document.getElementById("decide-error"), err.message);
        btn.disabled = false;
      }
    });
  }

  document.querySelectorAll(".rating-form").forEach((ratingForm) => {
    ratingForm.addEventListener("change", (e) => {
      if (e.target.name !== "tags") return;
      const checked = ratingForm.querySelectorAll("input[name=tags]:checked");
      if (checked.length > 3) e.target.checked = false;
    });
    ratingForm.addEventListener("submit", async (e) => {
      e.preventDefault();
      const error = ratingForm.querySelector(".error");
      const button = ratingForm.querySelector("button[type=submit]");
      const data = new FormData(ratingForm);
      error.hidden = true;
      button.disabled = true;
      try {
        await api(`/api/companions/${id}/ratings`, {
          method: "POST",
          body: JSON.stringify({
            targetId: Number(ratingForm.dataset.target),
            score: Number(data.get("score")),
            tags: data.getAll("tags"),
          }),
        });
        await renderCompanionDetail(id);
      } catch (err) {
        if (handleAuthError(err)) return;
        showError(error, err.message);
        button.disabled = false;
      }
    });
  });
}

function applicationCard(a, canDecide, postId) {
  const status = APPLICATION_STATUS[a.status] || APPLICATION_STATUS.PENDING;
  const report = postId && a.memberId
    ? `<a class="report-link" href="#/voc/new?type=REPORT_MEMBER&member=${a.memberId}&post=${postId}&name=${encodeURIComponent(a.nickname)}">신고</a>`
    : "";
  const block = postId && a.memberId
    ? `<button class="report-link linkish" type="button" data-block="${a.memberId}" data-name="${escapeHtml(a.nickname)}">차단</button>`
    : "";
  return `
    <article class="review application">
      <p class="meta"><strong>${escapeHtml(a.nickname)}</strong> ${verifiedBadge(a.phoneVerified)} <span class="badge ${status.cls}">${status.text}</span> ${report} ${block}</p>
      <p class="meta">${mannerLine(a.manner)}</p>
      <p>${escapeHtml(a.message)}</p>
      ${canDecide && a.status === "PENDING" ? `
        <div class="actions">
          <button class="primary" type="button" data-decide="ACCEPTED" data-app="${a.id}">수락</button>
          <button class="ghost" type="button" data-decide="REJECTED" data-app="${a.id}">거절</button>
        </div>` : ""}
    </article>
  `;
}

function ratingSection(targets) {
  return `
    <h2>함께한 동행 평가하기</h2>
    <p class="hint">평가는 상대에게 별점 평균과 좋은 태그로만 보여요. 나쁜 태그는 운영 참고용으로만 쓰여요.</p>
    ${targets.map((t) => t.rated ? `
      <div class="card"><p class="meta">${escapeHtml(t.nickname)}</p><p class="success">평가를 남겼어요. 고마워요!</p></div>
    ` : `
      <form class="card form rating-form" data-target="${t.memberId}">
        <h3>${escapeHtml(t.nickname)}님과의 약속은 어땠나요?</h3>
        <div class="form">
          <select name="score">
            <option value="5">★★★★★ 최고였어요</option>
            <option value="4">★★★★ 좋았어요</option>
            <option value="3">★★★ 보통이에요</option>
            <option value="2">★★ 아쉬웠어요</option>
            <option value="1">★ 별로였어요</option>
          </select>
          <div class="tag-picks">
            ${MANNER_TAGS.map(([code, label]) => `
              <label class="tag-pick"><input type="checkbox" name="tags" value="${code}"><span>${label}</span></label>
            `).join("")}
          </div>
          <p class="hint">태그는 3개까지 고를 수 있어요.</p>
        </div>
        <p class="error inline" hidden></p>
        <button class="primary" type="submit">평가 남기기</button>
      </form>
    `).join("")}
  `;
}

function renderCompanionForm() {
  if (!me) {
    location.hash = loginUrl("#/companion/new");
    return;
  }
  if (!me.phoneVerified) {
    view.innerHTML = `
      <h1>모집글 올리기</h1>
      <div class="notice">
        <p>같이가요는 안전을 위해 휴대폰 인증을 마친 회원만 글을 올릴 수 있어요.</p>
        <a class="primary block" href="#/me">휴대폰 인증하러 가기</a>
      </div>
      <a class="ghost block narrow-top" href="#/companion">← 목록으로</a>
    `;
    return;
  }
  const today = todayIso();
  view.innerHTML = `
    <h1>모집글 올리기</h1>
    <p class="lead">일정과 원하는 동행 스타일을 알려 주세요. ${escapeHtml(me.nickname)}님 이름으로 올라가요.</p>
    <form class="form" id="companion-form">
      <label class="field"><span>제목</span>
        <input name="title" maxlength="100" placeholder="예: 제주 3박 4일 렌터카 같이 타실 분" required>
      </label>
      <label class="field"><span>여행지</span>
        <input name="destination" maxlength="30" placeholder="예: 제주" value="제주" required>
      </label>
      <div class="row even">
        <label class="field"><span>시작일</span>
          <input type="date" name="startDate" min="${today}" value="${today}" required>
        </label>
        <label class="field"><span>종료일</span>
          <input type="date" name="endDate" min="${today}" value="${today}" required>
        </label>
      </div>
      <div class="row even">
        <label class="field"><span>모집 인원</span>
          <select name="recruitCount">
            ${[1, 2, 3, 4, 5].map((n) => `<option value="${n}">${n}명</option>`).join("")}
          </select>
        </label>
        <label class="field"><span>여행 스타일 (· 로 구분)</span>
          <input name="style" maxlength="40" placeholder="렌터카·맛집">
        </label>
      </div>
      <label class="field"><span>소개</span>
        <textarea name="content" rows="6" maxlength="2000" placeholder="일정, 이동 방법, 비용 분담, 원하는 동행 스타일을 자유롭게 적어 주세요." required></textarea>
      </label>
      <p class="error inline" id="companion-error" hidden></p>
      <button class="primary" type="submit">모집글 올리기</button>
      <a class="ghost block" href="#/companion">취소</a>
    </form>
  `;
  const form = document.getElementById("companion-form");
  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    const error = document.getElementById("companion-error");
    const button = form.querySelector("button[type=submit]");
    const data = new FormData(form);
    error.hidden = true;
    if (data.get("endDate") < data.get("startDate")) {
      showError(error, "종료일은 시작일보다 빠를 수 없어요.");
      return;
    }
    button.disabled = true;
    try {
      const created = await api("/api/companions", {
        method: "POST",
        body: JSON.stringify({
          title: data.get("title"),
          destination: data.get("destination"),
          startDate: data.get("startDate"),
          endDate: data.get("endDate"),
          recruitCount: Number(data.get("recruitCount")),
          content: data.get("content"),
          style: data.get("style"),
        }),
      });
      location.hash = `#/companion/${created.id}`;
    } catch (err) {
      if (handleAuthError(err)) return;
      showError(error, err.message);
      button.disabled = false;
    }
  });
}

/* ---------- 로그인 / 회원가입 / 마이 ---------- */

function renderLogin(params) {
  const next = safeNext(params.get("next"));
  if (me) {
    location.hash = next;
    return;
  }
  view.innerHTML = `
    <h1>다시 만나서<br>반가워요</h1>
    <p class="lead">로그인하고 후기와 동행 글을 남겨 보세요.</p>
    <form class="form" id="login-form" novalidate>
      <label class="field"><span>이메일</span>
        <input type="email" name="email" autocomplete="email" placeholder="you@example.com" required>
      </label>
      <label class="field"><span>비밀번호</span>
        <input type="password" name="password" data-password autocomplete="current-password" placeholder="비밀번호" required>
      </label>
      <label class="check"><input type="checkbox" data-toggle-password> 비밀번호 보기</label>
      <p class="error inline" id="login-error" hidden></p>
      <button class="primary" type="submit">로그인</button>
    </form>
    <p class="switch">아직 회원이 아니에요? <a href="#/signup?next=${encodeURIComponent(next)}">회원가입</a></p>
  `;
  const form = document.getElementById("login-form");
  bindPasswordToggle(form);
  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    const error = document.getElementById("login-error");
    const button = form.querySelector("button[type=submit]");
    const data = new FormData(form);
    error.hidden = true;
    button.disabled = true;
    try {
      const auth = await api("/api/auth/login", {
        method: "POST",
        body: JSON.stringify({ email: data.get("email"), password: data.get("password") }),
      });
      saveSession(auth);
      location.hash = next;
    } catch (err) {
      showError(error, err.message);
      button.disabled = false;
    }
  });
}

function renderSignup(params) {
  const next = safeNext(params.get("next"));
  if (me) {
    location.hash = next;
    return;
  }
  view.innerHTML = `
    <h1>WAYALONE 멤버가 되어<br>더 가볍게 떠나요</h1>
    <p class="lead">회원가입하면 후기를 남기고 같이가요에 합류할 수 있어요. 모르는 사람과 만나는 서비스라 휴대폰 인증을 꼭 거쳐요.</p>
    <form class="form" id="signup-form" novalidate>
      <label class="field"><span>이메일</span>
        <input type="email" name="email" autocomplete="email" placeholder="you@example.com" required>
      </label>
      <label class="field"><span>닉네임</span>
        <input name="nickname" maxlength="12" autocomplete="nickname" placeholder="2~12자 (한글·영문·숫자·_)" required>
      </label>
      ${phoneVerifyBlock()}
      <label class="field"><span>비밀번호</span>
        <input type="password" name="password" data-password autocomplete="new-password" placeholder="비밀번호" required>
        <p class="hint" id="pw-hint">${PASSWORD_HINT}</p>
      </label>
      <label class="field"><span>비밀번호 확인</span>
        <input type="password" name="passwordConfirm" data-password autocomplete="new-password" placeholder="비밀번호를 한 번 더" required>
      </label>
      <label class="check"><input type="checkbox" data-toggle-password> 비밀번호 보기</label>
      <p class="error inline" id="signup-error" hidden></p>
      <button class="primary" type="submit">회원가입하고 시작하기</button>
    </form>
    <p class="switch">이미 회원이에요? <a href="#/login?next=${encodeURIComponent(next)}">로그인</a></p>
  `;
  const form = document.getElementById("signup-form");
  bindPasswordToggle(form);
  const phone = bindPhoneVerify(form);
  const hint = document.getElementById("pw-hint");
  form.elements.password.addEventListener("input", (e) => {
    const ok = PASSWORD_RULE.test(e.target.value);
    hint.className = e.target.value ? (ok ? "hint good" : "hint bad") : "hint";
    hint.textContent = ok ? "좋아요, 안전한 비밀번호예요." : PASSWORD_HINT;
  });
  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    const error = document.getElementById("signup-error");
    const button = form.querySelector("button[type=submit]");
    const data = new FormData(form);
    error.hidden = true;
    if (!PASSWORD_RULE.test(data.get("password"))) {
      showError(error, PASSWORD_HINT);
      return;
    }
    if (data.get("password") !== data.get("passwordConfirm")) {
      showError(error, "비밀번호가 서로 달라요. 다시 확인해 주세요.");
      return;
    }
    if (!phone.token) {
      showError(error, "휴대폰 인증을 먼저 완료해 주세요.");
      return;
    }
    button.disabled = true;
    try {
      const auth = await api("/api/auth/signup", {
        method: "POST",
        body: JSON.stringify({
          email: data.get("email"),
          nickname: data.get("nickname"),
          password: data.get("password"),
          phone: phone.phone,
          phoneVerificationToken: phone.token,
        }),
      });
      saveSession(auth);
      location.hash = next;
    } catch (err) {
      showError(error, err.message);
      button.disabled = false;
    }
  });
}

function renderMe() {
  if (!me) {
    location.hash = loginUrl("#/me");
    return;
  }
  view.innerHTML = `
    <h1>${escapeHtml(me.nickname)}님,<br>반가워요</h1>
    <div class="info">
      <div><span class="k">닉네임</span><strong>${escapeHtml(me.nickname)}</strong></div>
      <div><span class="k">이메일</span><strong>${escapeHtml(me.email)}</strong></div>
      <div><span class="k">휴대폰</span><span>${verifiedBadge(me.phoneVerified)}</span></div>
      <div><span class="k">매너 평가</span><span id="my-manner">불러오는 중…</span></div>
    </div>
    ${me.phoneVerified ? "" : `
      <h2>휴대폰 인증</h2>
      <form class="form notice" id="phone-form" novalidate>
        <p>같이가요에 글을 쓰거나 신청하려면 휴대폰 인증이 필요해요.</p>
        ${phoneVerifyBlock()}
        <p class="error inline" id="phone-error" hidden></p>
        <button class="primary" type="submit">인증 완료하기</button>
      </form>
    `}
    ${me.role === "ADMIN" ? `<a class="primary block narrow-top" href="#/admin">운영 관리 페이지</a>` : ""}
    <h2>내 같이가요</h2>
    <div id="my-activity" class="empty">불러오는 중…</div>
    <h2>내 문의 · 신고</h2>
    <a class="ghost block" href="#/voc/new?type=INQUIRY">운영팀에 문의하기</a>
    <div id="my-vocs" class="empty narrow-top">불러오는 중…</div>
    <h2>비밀번호 바꾸기</h2>
    <form class="form" id="password-form" novalidate>
      <label class="field"><span>새 비밀번호</span>
        <input type="password" name="newPassword" data-password autocomplete="new-password" required>
        <p class="hint">${PASSWORD_HINT}</p>
      </label>
      <label class="field"><span>새 비밀번호 확인</span>
        <input type="password" name="newPasswordConfirm" data-password autocomplete="new-password" required>
      </label>
      <label class="check"><input type="checkbox" data-toggle-password> 비밀번호 보기</label>
      <p class="error inline" id="password-error" hidden></p>
      <p class="success" id="password-success" hidden></p>
      <button class="primary" type="submit">비밀번호 바꾸기</button>
    </form>
    <h2>최근 로그인 기록</h2>
    <p class="hint">내가 하지 않은 로그인이 보이면 바로 비밀번호를 바꿔 주세요.</p>
    <div id="my-logins" class="empty">불러오는 중…</div>
    <h2>차단한 회원</h2>
    <div id="my-blocks" class="empty">불러오는 중…</div>
    <h2>계정</h2>
    <button class="ghost block" id="logout" type="button">로그아웃</button>
    <details class="withdraw">
      <summary>회원 탈퇴</summary>
      <form class="form notice" id="withdraw-form" novalidate>
        <p><strong>탈퇴하면 되돌릴 수 없어요.</strong></p>
        <ul class="plain-list">
          <li>이메일·닉네임·휴대폰 인증 정보가 삭제(비식별화)되고 바로 로그아웃돼요.</li>
          <li>내가 올린 모집글은 삭제되고, 답을 받지 못한 신청은 취소돼요.</li>
          <li>남긴 후기와 평가, 신고 기록은 서비스 운영을 위해 "탈퇴한회원"으로 남아요.</li>
        </ul>
        <label class="field"><span>비밀번호 확인</span>
          <input type="password" name="password" autocomplete="current-password" required>
        </label>
        <label class="field"><span>탈퇴 사유 (선택)</span>
          <input name="reason" maxlength="200" placeholder="서비스 개선에 참고할게요">
        </label>
        <p class="error inline" id="withdraw-error" hidden></p>
        <button class="primary danger" type="submit">탈퇴하기</button>
      </form>
    </details>
  `;
  loadMyLogins();
  loadMyBlocks();
  const withdrawForm = document.getElementById("withdraw-form");
  withdrawForm.addEventListener("submit", async (e) => {
    e.preventDefault();
    const error = document.getElementById("withdraw-error");
    const data = new FormData(withdrawForm);
    error.hidden = true;
    if (!data.get("password")) {
      showError(error, "비밀번호를 입력해 주세요.");
      return;
    }
    if (!window.confirm("정말 탈퇴할까요? 이 작업은 되돌릴 수 없어요.")) return;
    const button = withdrawForm.querySelector("button[type=submit]");
    button.disabled = true;
    try {
      await api("/api/account/withdraw", {
        method: "POST",
        body: JSON.stringify({ password: data.get("password"), reason: data.get("reason") }),
      });
      clearSession();
      view.innerHTML = `
        <h1>탈퇴가 완료됐어요</h1>
        <p class="lead">그동안 WAYALONE을 이용해 주셔서 고마워요. 개인정보는 비식별 처리됐어요.</p>
        <a class="primary block" href="#/">처음으로</a>
      `;
    } catch (err) {
      if (err.status !== 400 && handleAuthError(err)) return;
      showError(error, err.message);
      button.disabled = false;
    }
  });
  api(`/api/members/${me.id}/profile`)
    .then((profile) => { document.getElementById("my-manner").innerHTML = mannerLine(profile.manner); })
    .catch(() => { document.getElementById("my-manner").textContent = "-"; });
  loadMyActivity();

  const phoneForm = document.getElementById("phone-form");
  if (phoneForm) {
    const phone = bindPhoneVerify(phoneForm);
    phoneForm.addEventListener("submit", async (e) => {
      e.preventDefault();
      const error = document.getElementById("phone-error");
      error.hidden = true;
      if (!phone.token) {
        showError(error, "인증번호 확인까지 마쳐 주세요.");
        return;
      }
      try {
        me = await api("/api/auth/phone", {
          method: "POST",
          body: JSON.stringify({ phone: phone.phone, phoneVerificationToken: phone.token }),
        });
        renderAccount();
        renderMe();
      } catch (err) {
        if (handleAuthError(err)) return;
        showError(error, err.message);
      }
    });
  }

  const form = document.getElementById("password-form");
  bindPasswordToggle(form);
  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    const error = document.getElementById("password-error");
    const success = document.getElementById("password-success");
    const button = form.querySelector("button[type=submit]");
    const data = new FormData(form);
    error.hidden = true;
    success.hidden = true;
    if (!PASSWORD_RULE.test(data.get("newPassword"))) {
      showError(error, PASSWORD_HINT);
      return;
    }
    if (data.get("newPassword") !== data.get("newPasswordConfirm")) {
      showError(error, "새 비밀번호가 서로 달라요. 다시 확인해 주세요.");
      return;
    }
    button.disabled = true;
    try {
      const auth = await api("/api/auth/password", {
        method: "POST",
        body: JSON.stringify({ newPassword: data.get("newPassword") }),
      });
      saveSession(auth);
      form.reset();
      success.hidden = false;
      success.textContent = "비밀번호를 바꿨어요. 다른 기기에서는 다시 로그인해야 해요.";
    } catch (err) {
      if (handleAuthError(err)) return;
      showError(error, err.message);
    }
    button.disabled = false;
  });
  document.getElementById("logout").addEventListener("click", async () => {
    try {
      await api("/api/auth/logout", { method: "POST" });
    } catch {
      /* 이미 만료된 토큰이어도 이 기기에서는 로그아웃 처리합니다. */
    }
    clearSession();
    location.hash = "#/";
  });
}

/* ---------- 문의 · 신고 (VOC) ---------- */

const VOC_TYPE = { INQUIRY: "문의", REPORT_MEMBER: "회원 신고", REPORT_POST: "게시글 신고", BUG: "오류 제보" };
const VOC_STATUS = {
  OPEN: { text: "접수", cls: "open" },
  IN_PROGRESS: { text: "처리 중", cls: "counter" },
  RESOLVED: { text: "답변 완료", cls: "yes" },
};

function fmtDateTime(iso) {
  return iso ? new Date(iso).toLocaleString("ko-KR", { month: "numeric", day: "numeric", hour: "2-digit", minute: "2-digit" }) : "-";
}

function vocBadges(v) {
  const st = VOC_STATUS[v.status];
  return `<span class="badge unknown">${VOC_TYPE[v.type]}</span> <span class="badge ${st.cls}">${st.text}</span>`;
}

function renderVocForm(params) {
  if (!me) {
    location.hash = loginUrl(location.hash);
    return;
  }
  const type = VOC_TYPE[params.get("type")] ? params.get("type") : "INQUIRY";
  const memberId = params.get("member");
  const postId = params.get("post");
  const name = params.get("name");
  const isReport = type.startsWith("REPORT");
  view.innerHTML = `
    <h1>${isReport ? "신고하기" : "운영팀에 문의하기"}</h1>
    <p class="lead">${isReport
      ? "신고 내용은 상대에게 알려지지 않아요. 운영팀이 확인 후 마이 페이지로 결과를 알려 드려요."
      : "궁금한 점이나 불편한 점을 남겨 주세요. 답변은 마이 페이지에서 확인할 수 있어요."}</p>
    <form class="form" id="voc-form">
      ${isReport ? `
        <div class="info">
          <div><span class="k">신고 대상</span><strong>${type === "REPORT_POST" ? `모집글 #${escapeHtml(postId)}` : escapeHtml(name || `회원 #${memberId}`)}</strong></div>
        </div>` : `
        <label class="field"><span>종류</span>
          <select name="type">
            <option value="INQUIRY">문의</option>
            <option value="BUG">오류 제보</option>
          </select>
        </label>`}
      <label class="field"><span>내용</span>
        <textarea name="content" rows="6" maxlength="1000" required placeholder="${isReport
          ? "어떤 일이 있었는지 구체적으로 적어 주세요. (예: 약속 장소에 나오지 않았어요, 불쾌한 메시지를 보냈어요)"
          : "내용을 적어 주세요."}"></textarea>
      </label>
      <p class="error inline" id="voc-error" hidden></p>
      <button class="primary" type="submit">${isReport ? "신고 접수하기" : "문의 남기기"}</button>
      <a class="ghost block" href="javascript:history.back()">취소</a>
    </form>
  `;
  const form = document.getElementById("voc-form");
  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    const data = new FormData(form);
    const error = document.getElementById("voc-error");
    const button = form.querySelector("button[type=submit]");
    error.hidden = true;
    button.disabled = true;
    try {
      await api("/api/vocs", {
        method: "POST",
        body: JSON.stringify({
          type: isReport ? type : data.get("type"),
          content: data.get("content"),
          targetMemberId: memberId ? Number(memberId) : null,
          targetPostId: postId ? Number(postId) : null,
        }),
      });
      view.innerHTML = `
        <h1>접수됐어요</h1>
        <p class="lead">운영팀이 확인한 뒤 마이 페이지의 "내 문의"로 결과를 알려 드릴게요.</p>
        <a class="primary block" href="#/me">마이 페이지로</a>
      `;
    } catch (err) {
      if (handleAuthError(err)) return;
      showError(error, err.message);
      button.disabled = false;
    }
  });
}

async function loadMyLogins() {
  const box = document.getElementById("my-logins");
  try {
    const logins = await api("/api/auth/logins");
    box.className = logins.length ? "" : "empty";
    box.innerHTML = logins.length ? loginRows(logins, true) : "로그인 기록이 아직 없어요.";
  } catch {
    box.textContent = "-";
  }
}

async function loadMyBlocks() {
  const box = document.getElementById("my-blocks");
  try {
    const blocks = await api("/api/blocks");
    box.className = blocks.length ? "" : "empty";
    box.innerHTML = blocks.length
      ? blocks.map((b) => `
        <article class="review">
          <p class="meta"><strong>${escapeHtml(b.nickname)}</strong> · ${fmtDateTime(b.blockedAt)}부터 차단
            <button class="report-link linkish" type="button" data-unblock="${b.memberId}">차단 해제</button></p>
        </article>`).join("")
      : "차단한 회원이 없어요.";
    box.querySelectorAll("[data-unblock]").forEach((btn) => {
      btn.addEventListener("click", async () => {
        try {
          await api(`/api/blocks/${btn.dataset.unblock}`, { method: "DELETE" });
          loadMyBlocks();
        } catch (err) {
          if (handleAuthError(err)) return;
          window.alert(err.message);
        }
      });
    });
  } catch {
    box.textContent = "-";
  }
}

async function loadMyActivity() {
  const box = document.getElementById("my-activity");
  const vocBox = document.getElementById("my-vocs");
  try {
    const [mine, vocs] = await Promise.all([api("/api/companions/mine"), api("/api/vocs/mine")]);
    box.className = "";
    box.innerHTML = `
      <div class="grid-2 compact">
        <a class="tile" href="#/companion?scope=posts"><strong>${mine.posts.length}</strong><span>내 모집글</span></a>
        <a class="tile" href="#/companion?scope=applied"><strong>${mine.applications.length}</strong><span>내 신청</span></a>
      </div>
    `;
    vocBox.className = vocs.length ? "" : "empty";
    vocBox.innerHTML = vocs.length
      ? vocs.map((v) => `
        <article class="card">
          <p class="meta">${vocBadges(v)} ${fmtDateTime(v.createdAt)}${v.targetMemberNickname ? ` · 대상: ${escapeHtml(v.targetMemberNickname)}` : ""}</p>
          <p class="post-body">${escapeHtml(v.content)}</p>
          ${v.answer ? `<div class="answer"><strong>운영팀 답변</strong><p>${escapeHtml(v.answer)}</p></div>` : ""}
        </article>`).join("")
      : "아직 남긴 문의가 없어요.";
  } catch {
    box.textContent = "-";
  }
}

/* ---------- 관리자 ---------- */

const MEMBER_STATUS = {
  ACTIVE: { text: "정상", cls: "yes" },
  SUSPENDED: { text: "기간 정지", cls: "counter" },
  BANNED: { text: "영구 차단", cls: "no" },
  WITHDRAWN: { text: "탈퇴", cls: "unknown" },
};
const LOGIN_RESULT = {
  SUCCESS: { text: "로그인 성공", cls: "yes" },
  FAIL_CREDENTIALS: { text: "비밀번호 오류", cls: "no" },
  FAIL_BLOCKED: { text: "제한된 계정", cls: "counter" },
  FAIL_LOCKED: { text: "연속 실패로 잠김", cls: "no" },
};

/** User-Agent 를 "Windows · Chrome" 정도로 줄여 보여 줍니다. */
function shortDevice(ua) {
  if (!ua) return "알 수 없는 기기";
  const os = /Windows/.test(ua) ? "Windows" : /Android/.test(ua) ? "Android" : /iPhone|iPad/.test(ua) ? "iOS"
    : /Mac OS X/.test(ua) ? "Mac" : /Linux/.test(ua) ? "Linux" : "기타";
  const browser = /Edg\//.test(ua) ? "Edge" : /Chrome\//.test(ua) ? "Chrome" : /Safari\//.test(ua) ? "Safari"
    : /Firefox\//.test(ua) ? "Firefox" : "";
  return browser ? `${os} · ${browser}` : os;
}

function loginRows(list, showIp) {
  return list.map((l) => {
    const r = LOGIN_RESULT[l.result] || { text: l.result, cls: "unknown" };
    return `
      <article class="review">
        <p class="meta">${fmtDateTime(l.createdAt)} · <span class="badge ${r.cls}">${r.text}</span></p>
        <p class="meta">${escapeHtml(shortDevice(l.userAgent))}${showIp ? ` · IP ${escapeHtml(l.ip || "-")}` : ""}</p>
      </article>`;
  }).join("");
}
const ACTION_LABEL = {
  SUSPEND: "기간 정지", BAN: "영구 차단", RELEASE: "제한 해제",
  HIDE_POST: "글 숨김", SHOW_POST: "글 다시 보이기", VOC_UPDATE: "VOC 처리", REVERT: "되돌리기",
};

/** 활동 로그 한 줄. 되돌릴 수 있는 처리에는 버튼을, 되돌린 처리에는 표시를 붙입니다. */
function actionEntry(a, targetHtml) {
  const reverted = a.revertedAt
    ? `<span class="badge unknown">되돌림 · ${escapeHtml(a.revertedByNickname || "")} ${fmtDateTime(a.revertedAt)}</span>`
    : "";
  return `
    <article class="review action-entry ${a.revertedAt ? "is-reverted" : ""}">
      <p class="meta">#${a.id} · ${fmtDateTime(a.createdAt)} · ${escapeHtml(a.adminNickname)}${targetHtml ? ` · ${targetHtml}` : ""}</p>
      <p><strong>${ACTION_LABEL[a.action] || a.action}</strong> ${escapeHtml(a.detail || "")} ${reverted}</p>
      ${a.revertible ? `<button class="ghost revert-btn" type="button" data-revert="${a.id}" data-label="${escapeHtml(ACTION_LABEL[a.action] || a.action)}">되돌리기</button>` : ""}
    </article>`;
}

function bindRevertButtons() {
  view.querySelectorAll("[data-revert]").forEach((btn) => {
    btn.addEventListener("click", async () => {
      const reason = window.prompt(`#${btn.dataset.revert} '${btn.dataset.label}'을(를) 처리 전 상태로 되돌릴까요?\n되돌리는 이유를 적어 주세요. (기록에 남아요)`);
      if (reason === null) return;
      if (!reason.trim()) {
        window.alert("되돌리는 이유를 입력해 주세요.");
        return;
      }
      btn.disabled = true;
      try {
        await api(`/api/admin/actions/${btn.dataset.revert}/revert`, {
          method: "POST",
          body: JSON.stringify({ reason }),
        });
        route();
      } catch (err) {
        if (handleAuthError(err)) return;
        window.alert(err.message);
        btn.disabled = false;
      }
    });
  });
}

function memberStatusBadge(m) {
  const st = MEMBER_STATUS[m.blocked ? m.status : "ACTIVE"] || MEMBER_STATUS.ACTIVE;
  const until = m.blocked && m.status === "SUSPENDED" ? ` ~${fmtDateTime(m.suspendedUntil)}` : "";
  return `<span class="badge ${st.cls}">${st.text}${until}</span>`;
}

function adminNav(active, stats) {
  const open = stats ? stats.openVocs : 0;
  const tabs = [
    ["dashboard", "대시보드", "#/admin"],
    ["members", "회원", "#/admin/members"],
    ["vocs", `VOC${open ? ` <b class="count">${open}</b>` : ""}`, "#/admin/vocs"],
    ["actions", "활동 로그", "#/admin/actions"],
  ];
  return `
    <div class="admin-head">
      <h1>운영 관리</h1>
      <p class="lead">회원, 신고·문의, 제재를 한곳에서 관리해요. 모든 처리는 활동 로그에 남고, 잘못 처리한 건은 되돌릴 수 있어요.</p>
      <nav class="segmented">${tabs.map(([key, label, href]) => `<a class="${key === active ? "on" : ""}" href="${href}">${label}</a>`).join("")}</nav>
    </div>
  `;
}

async function renderAdmin(section, sub, params) {
  if (!me) {
    location.hash = loginUrl(location.hash);
    return;
  }
  if (me.role !== "ADMIN") {
    view.innerHTML = `<p class="error">관리자만 볼 수 있는 페이지예요.</p>`;
    return;
  }
  view.innerHTML = `<p class="empty">불러오는 중이에요…</p>`;
  try {
    const stats = await api("/api/admin/stats");
    if (section === "members" && sub) return await renderAdminMember(sub, stats);
    if (section === "members") return await renderAdminMembers(params, stats);
    if (section === "vocs") return await renderAdminVocs(params, stats);
    if (section === "actions") return await renderAdminActions(stats);
    return renderAdminDashboard(stats);
  } catch (err) {
    if (handleAuthError(err)) return;
    view.innerHTML = `<p class="error">${escapeHtml(err.message)}</p>`;
  }
}

const CHART_COLORS = {
  green: "#03C75A", orange: "#e8803a", red: "#d64532", blue: "#3f7fbf", gray: "#cdbfae", brown: "#a8431a",
};

/** 최근 14일 막대 차트 (SVG, 외부 라이브러리 없음) */
function barChart(points, key, color, title) {
  const h = 110;
  const w = 22;
  const gap = 8;
  const max = Math.max(1, ...points.map((p) => p[key]));
  const total = points.reduce((sum, p) => sum + p[key], 0);
  const width = points.length * (w + gap) - gap;
  const bars = points.map((p, i) => {
    const value = p[key];
    const bh = value ? Math.max(4, Math.round((value / max) * (h - 22))) : 2;
    const x = i * (w + gap);
    const y = h - bh;
    const [, mm, dd] = p.date.split("-");
    const label = i % 2 === points.length % 2 || i === points.length - 1 ? `${Number(mm)}/${Number(dd)}` : "";
    return `
      <rect x="${x}" y="${y}" width="${w}" height="${bh}" rx="5" fill="${value ? color : CHART_COLORS.gray}" opacity="${value ? 1 : 0.5}">
        <title>${p.date}: ${value}</title>
      </rect>
      ${value ? `<text x="${x + w / 2}" y="${y - 5}" text-anchor="middle" class="bar-value">${value}</text>` : ""}
      ${label ? `<text x="${x + w / 2}" y="${h + 16}" text-anchor="middle" class="bar-label">${label}</text>` : ""}`;
  }).join("");
  return `
    <figure class="chart">
      <figcaption><strong>${title}</strong><span>14일 ${total.toLocaleString()}건 · 오늘 ${points[points.length - 1][key]}</span></figcaption>
      <svg viewBox="0 -4 ${width} ${h + 24}" role="img" aria-label="${title} 최근 14일">${bars}</svg>
    </figure>`;
}

/** 도넛 차트. segments: [[이름, 값, 색], ...] */
function donutChart(title, segments, centerLabel) {
  const total = segments.reduce((sum, [, v]) => sum + v, 0);
  const r = 42;
  const c = 2 * Math.PI * r;
  let offset = 0;
  const rings = total
    ? segments.filter(([, v]) => v > 0).map(([name, v, color]) => {
      const len = (v / total) * c;
      const ring = `<circle r="${r}" cx="60" cy="60" fill="none" stroke="${color}" stroke-width="16"
        stroke-dasharray="${len} ${c - len}" stroke-dashoffset="${-offset}"><title>${name}: ${v}</title></circle>`;
      offset += len;
      return ring;
    }).join("")
    : `<circle r="${r}" cx="60" cy="60" fill="none" stroke="${CHART_COLORS.gray}" stroke-width="16" opacity="0.5"/>`;
  return `
    <figure class="chart donut">
      <figcaption><strong>${title}</strong></figcaption>
      <div class="donut-body">
        <svg viewBox="0 0 120 120" role="img" aria-label="${title}">
          <g transform="rotate(-90 60 60)">${rings}</g>
          <text x="60" y="58" text-anchor="middle" class="donut-value">${centerLabel}</text>
          <text x="60" y="76" text-anchor="middle" class="donut-sub">전체 ${total.toLocaleString()}</text>
        </svg>
        <ul class="legend">
          ${segments.map(([name, v, color]) => `
            <li><i style="background:${color}"></i>${name}<b>${v.toLocaleString()}</b><em>${total ? Math.round((v / total) * 100) : 0}%</em></li>`).join("")}
        </ul>
      </div>
    </figure>`;
}

function renderAdminDashboard(s) {
  const pct = (part, whole) => (whole ? `${Math.round((part / whole) * 100)}%` : "0%");
  const kpi = (label, value, sub, href) => `
    <${href ? `a href="${href}"` : "div"} class="stat">
      <span>${label}</span><strong>${value.toLocaleString()}</strong>${sub ? `<em>${sub}</em>` : ""}
    </${href ? "a" : "div"}>`;
  const active = s.members - s.suspended - s.banned - s.withdrawn;
  view.innerHTML = `
    ${adminNav("dashboard", s)}
    ${s.openVocs ? `<a class="notice alert-row" href="#/admin/vocs?status=OPEN"><p><strong>처리하지 않은 VOC ${s.openVocs}건</strong>이 있어요. 신고는 빠르게 확인해 주세요 →</p></a>` : ""}
    <div class="stats">
      ${kpi("전체 회원", s.members, `오늘 +${s.newMembersToday} · 7일 +${s.newMembersWeek}`, "#/admin/members")}
      ${kpi("모집글", s.posts, s.hiddenPosts ? `숨김 ${s.hiddenPosts}` : "")}
      ${kpi("합류 신청", s.applications, "")}
      ${kpi("미처리 VOC", s.openVocs, `처리 중 ${s.inProgressVocs}`, "#/admin/vocs?status=OPEN")}
      ${kpi("탈퇴 회원", s.withdrawn, "개인정보 비식별 처리됨", "#/admin/members?status=WITHDRAWN")}
      ${kpi("차단된 번호", s.blacklistedPhones, "재가입 불가", "")}
    </div>

    <h2>최근 14일 추이</h2>
    <div class="charts">
      ${barChart(s.daily, "members", CHART_COLORS.green, "신규 가입")}
      ${barChart(s.daily, "posts", CHART_COLORS.orange, "새 모집글")}
      ${barChart(s.daily, "applications", CHART_COLORS.blue, "합류 신청")}
      ${barChart(s.daily, "vocs", CHART_COLORS.red, "VOC 접수")}
    </div>

    <h2>현황 비율</h2>
    <div class="charts">
      ${donutChart("회원 상태", [
        ["정상", active, CHART_COLORS.green],
        ["기간 정지", s.suspended, CHART_COLORS.orange],
        ["영구 차단", s.banned, CHART_COLORS.red],
        ["탈퇴", s.withdrawn, CHART_COLORS.gray],
      ], pct(active, s.members))}
      ${donutChart("휴대폰 인증", [
        ["인증", s.phoneVerified, CHART_COLORS.green],
        ["미인증", s.members - s.phoneVerified, CHART_COLORS.gray],
      ], pct(s.phoneVerified, s.members))}
      ${donutChart("VOC 처리", [
        ["접수", s.openVocs, CHART_COLORS.red],
        ["처리 중", s.inProgressVocs, CHART_COLORS.orange],
        ["답변 완료", s.resolvedVocs, CHART_COLORS.green],
      ], pct(s.resolvedVocs, s.openVocs + s.inProgressVocs + s.resolvedVocs))}
      ${donutChart("동행 평가 중 노쇼 태그", [
        ["일반 평가", s.ratings - s.noShows, CHART_COLORS.green],
        ["노쇼 태그", s.noShows, CHART_COLORS.red],
      ], pct(s.noShows, s.ratings))}
    </div>
  `;
}

async function renderAdminMembers(params, stats) {
  const q = params.get("q") || "";
  const status = params.get("status") || "";
  const page = Number(params.get("page") || 0);
  const query = new URLSearchParams({ page });
  if (q) query.set("q", q);
  if (status) query.set("status", status);
  const data = await api(`/api/admin/members?${query}`);
  const link = (over) => {
    const next = new URLSearchParams({ q, status, page, ...over });
    [...next.keys()].forEach((k) => { if (!next.get(k) || next.get(k) === "0") next.delete(k); });
    return `#/admin/members?${next}`;
  };
  view.innerHTML = `
    ${adminNav("members", stats)}
    <form class="search" id="admin-member-search">
      <input name="q" value="${escapeHtml(q)}" placeholder="닉네임 또는 이메일">
      <button class="primary" type="submit">검색</button>
    </form>
    <div class="filters">
      ${[["", "전체"], ["ACTIVE", "정상"], ["SUSPENDED", "기간 정지"], ["BANNED", "영구 차단"], ["WITHDRAWN", "탈퇴"]]
        .map(([v, label]) => `<a class="chip ${status === v ? "on" : ""}" href="${link({ status: v, page: 0 })}">${label}</a>`).join("")}
    </div>
    <p class="hint">${data.total.toLocaleString()}명 · 신고가 많거나 동행 온도가 낮은 회원을 먼저 살펴보세요.</p>
    <div class="admin-list">
      ${data.members.length ? data.members.map((m) => `
        <a class="card admin-row" href="#/admin/members/${m.id}">
          <div>
            <h3>${escapeHtml(m.nickname)} ${m.role === "ADMIN" ? `<span class="badge mine">관리자</span>` : ""}</h3>
            <p class="meta">${escapeHtml(m.email)} · 가입 ${fmtDateTime(m.createdAt)}</p>
          </div>
          <div class="admin-row-side">
            ${memberStatusBadge(m)} ${verifiedBadge(m.phoneVerified)}
            <p class="meta">${mannerLine(m.manner)}${m.manner.noShows ? ` · <b class="bad-text">노쇼 ${m.manner.noShows}</b>` : ""}${m.reportCount ? ` · <b class="bad-text">신고 ${m.reportCount}</b>` : ""}</p>
          </div>
        </a>`).join("") : `<p class="empty">조건에 맞는 회원이 없어요.</p>`}
    </div>
    <div class="pager">
      ${data.page > 0 ? `<a class="ghost" href="${link({ page: data.page - 1 })}">← 이전</a>` : "<span></span>"}
      <span class="meta">${data.totalPages ? data.page + 1 : 0} / ${data.totalPages}</span>
      ${data.page + 1 < data.totalPages ? `<a class="ghost" href="${link({ page: data.page + 1 })}">다음 →</a>` : "<span></span>"}
    </div>
  `;
  document.getElementById("admin-member-search").addEventListener("submit", (e) => {
    e.preventDefault();
    location.hash = link({ q: new FormData(e.target).get("q").trim(), page: 0 });
  });
}

function vocCardsReadonly(list, empty) {
  return list.length ? list.map((v) => `
    <article class="card">
      <p class="meta">${vocBadges(v)} ${fmtDateTime(v.createdAt)} · ${escapeHtml(v.reporterNickname)}${v.targetMemberNickname ? ` → ${escapeHtml(v.targetMemberNickname)}` : ""}</p>
      <p class="post-body">${escapeHtml(v.content)}</p>
      ${v.answer ? `<p class="meta">답변: ${escapeHtml(v.answer)}</p>` : ""}
      <a class="report-link" href="#/admin/vocs?status=ALL">VOC에서 처리 →</a>
    </article>`).join("") : `<p class="empty">${empty}</p>`;
}

async function renderAdminMember(id, stats) {
  const d = await api(`/api/admin/members/${id}`);
  const m = d.member;
  view.innerHTML = `
    ${adminNav("members", stats)}
    <a class="back" href="#/admin/members">← 회원 목록</a>
    <h2 class="admin-member-name">${escapeHtml(m.nickname)} ${memberStatusBadge(m)} ${verifiedBadge(m.phoneVerified)}</h2>
    <div class="info">
      <div><span class="k">이메일</span><span>${escapeHtml(m.email)}</span></div>
      <div><span class="k">가입</span><span>${fmtDateTime(m.createdAt)}</span></div>
      <div><span class="k">동행 온도</span><span>${mannerLine(m.manner)}</span></div>
      <div><span class="k">노쇼 / 신고</span><span><b class="${m.manner.noShows ? "bad-text" : ""}">노쇼 ${m.manner.noShows}회</b> · <b class="${m.reportCount ? "bad-text" : ""}">받은 신고 ${m.reportCount}건</b></span></div>
      ${m.statusReason ? `<div><span class="k">제재 사유</span><span>${escapeHtml(m.statusReason)}</span></div>` : ""}
    </div>

    ${d.withdrawal ? `
      <h2>탈퇴 기록</h2>
      <div class="info">
        <div><span class="k">탈퇴 시각</span><span>${fmtDateTime(d.withdrawal.withdrawnAt)}</span></div>
        <div><span class="k">탈퇴 사유</span><span>${escapeHtml(d.withdrawal.reason || "입력 안 함")}</span></div>
        <div><span class="k">정리된 항목</span><span>모집글 삭제 ${d.withdrawal.deletedPosts}건 · 대기 중 신청 취소 ${d.withdrawal.canceledApplications}건</span></div>
      </div>
      <p class="hint">이메일·닉네임·휴대폰 번호 해시는 비식별 값으로 바뀌었어요. 신고, 평가, 제재 이력과 로그인 기록은 보관 기간 동안 남아요.</p>` : ""}

    ${m.role === "ADMIN" || d.withdrawal ? "" : `
      <h2>이용 제재</h2>
      <form class="form notice" id="sanction-form">
        <div class="row even">
          <label class="field"><span>처리</span>
            <select name="action">
              <option value="SUSPEND">기간 정지</option>
              <option value="BAN">영구 차단 (블랙리스트)</option>
              <option value="RELEASE">제한 해제</option>
            </select>
          </label>
          <label class="field" data-days><span>정지 기간(일)</span>
            <input type="number" name="days" min="1" max="365" value="7">
          </label>
        </div>
        <label class="field"><span>사유 (회원이 로그인할 때 안내돼요)</span>
          <input name="reason" maxlength="200" placeholder="예: 반복적인 노쇼, 불쾌한 언행 신고 누적">
        </label>
        <p class="hint">정지·차단하면 즉시 로그아웃돼요. 영구 차단된 휴대폰 번호로는 다시 가입할 수 없어요.</p>
        <p class="error inline" id="sanction-error" hidden></p>
        <button class="primary" type="submit">적용하기</button>
      </form>`}

    <h2>받은 동행 평가 ${d.ratings.length}</h2>
    ${d.ratings.length ? d.ratings.map((r) => `
      <article class="review">
        <p class="meta">${fmtDateTime(r.createdAt)} · ${escapeHtml(r.raterNickname)} · 모집글 #${r.postId}</p>
        <p>${r.attended ? `★ ${r.score}` : `<span class="badge no">노쇼</span>`} ${r.tags.map((t) => `<span class="tag">${escapeHtml(t)}</span>`).join(" ")}</p>
      </article>`).join("") : `<p class="empty">받은 평가가 없어요.</p>`}

    <h2>받은 신고 ${d.reportsAbout.length}</h2>
    ${vocCardsReadonly(d.reportsAbout, "받은 신고가 없어요.")}

    <h2>모집글 ${d.posts.length}</h2>
    ${d.posts.length ? d.posts.map((p) => `
      <article class="review">
        <p class="meta">${fmtDateTime(p.createdAt)} · ${escapeHtml(p.destination)} · 신청 ${p.applicantCount}명 ${p.hidden ? `<span class="badge no">숨김</span>` : ""} ${p.deleted ? `<span class="badge unknown">삭제됨</span>` : ""}</p>
        <p>${p.deleted ? escapeHtml(p.title) : `<a href="#/companion/${p.id}">${escapeHtml(p.title)}</a>`}</p>
        ${p.deleted ? "" : `<button class="ghost" type="button" data-visibility="${p.id}" data-hidden="${!p.hidden}">${p.hidden ? "다시 보이기" : "숨기기"}</button>`}
      </article>`).join("") : `<p class="empty">올린 모집글이 없어요.</p>`}

    <h2>신청 내역 ${d.applications.length}</h2>
    ${d.applications.length ? d.applications.map((a) => `
      <article class="review">
        <p class="meta">${fmtDateTime(a.createdAt)} · <span class="badge ${APPLICATION_STATUS[a.status].cls}">${APPLICATION_STATUS[a.status].text}</span> · <a href="#/companion/${a.postId}">${escapeHtml(a.postTitle)}</a></p>
        <p>${escapeHtml(a.message)}</p>
      </article>`).join("") : `<p class="empty">신청 내역이 없어요.</p>`}

    <h2>이 회원이 남긴 문의 ${d.vocsBy.length}</h2>
    ${vocCardsReadonly(d.vocsBy, "남긴 문의가 없어요.")}

    <h2>제재 이력</h2>
    ${d.actions.length ? d.actions.map((a) => actionEntry(a, "")).join("") : `<p class="empty">제재 이력이 없어요.</p>`}

    <h2>삭제 기록 ${d.deletions.length}</h2>
    ${d.deletions.length ? d.deletions.map((x) => `
      <article class="review">
        <p class="meta">${fmtDateTime(x.createdAt)} · 모집글 #${x.targetId} · ${x.reason === "WITHDRAWAL" ? "탈퇴하며 삭제" : "작성자가 삭제"}</p>
        <p class="post-body">${escapeHtml(x.snapshot || "")}</p>
      </article>`).join("") : `<p class="empty">삭제한 글이 없어요.</p>`}

    <h2>최근 로그인 기록 ${d.recentLogins.length}</h2>
    ${d.recentLogins.length ? loginRows(d.recentLogins, true) : `<p class="empty">로그인 기록이 없어요.</p>`}
  `;
  bindRevertButtons();

  const form = document.getElementById("sanction-form");
  if (form) {
    const days = form.querySelector("[data-days]");
    form.elements.action.addEventListener("change", (e) => { days.hidden = e.target.value !== "SUSPEND"; });
    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      const data = new FormData(form);
      const error = document.getElementById("sanction-error");
      error.hidden = true;
      const action = data.get("action");
      if (action !== "RELEASE" && !window.confirm(`${m.nickname}님을 ${action === "BAN" ? "영구 차단" : `${data.get("days")}일 정지`}할까요?`)) return;
      try {
        await api(`/api/admin/members/${id}/sanction`, {
          method: "POST",
          body: JSON.stringify({
            action,
            days: action === "SUSPEND" ? Number(data.get("days")) : null,
            reason: data.get("reason"),
          }),
        });
        route();
      } catch (err) {
        if (handleAuthError(err)) return;
        showError(error, err.message);
      }
    });
  }
  view.querySelectorAll("[data-visibility]").forEach((btn) => {
    btn.addEventListener("click", async () => {
      const hidden = btn.dataset.hidden === "true";
      const reason = window.prompt(hidden ? "숨기는 이유를 적어 주세요." : "다시 보이게 하는 이유를 적어 주세요.");
      if (reason === null) return;
      try {
        await api(`/api/admin/companions/${btn.dataset.visibility}/visibility`, {
          method: "PATCH",
          body: JSON.stringify({ hidden, reason }),
        });
        route();
      } catch (err) {
        if (handleAuthError(err)) return;
        window.alert(err.message);
      }
    });
  });
}

async function renderAdminVocs(params, stats) {
  const status = params.get("status") || "OPEN";
  const list = await api(`/api/admin/vocs${status === "ALL" ? "" : `?status=${status}`}`);
  view.innerHTML = `
    ${adminNav("vocs", stats)}
    <div class="filters">
      ${[["OPEN", "접수"], ["IN_PROGRESS", "처리 중"], ["RESOLVED", "답변 완료"], ["ALL", "전체"]]
        .map(([v, label]) => `<a class="chip ${status === v ? "on" : ""}" href="#/admin/vocs?status=${v}">${label}</a>`).join("")}
    </div>
    ${list.length ? list.map((v) => `
      <form class="card form voc-admin" id="voc-${v.id}" data-id="${v.id}">
        <p class="meta">${vocBadges(v)} #${v.id} · ${fmtDateTime(v.createdAt)}</p>
        <p class="meta">
          보낸 사람: <a href="#/admin/members/${v.reporterId}">${escapeHtml(v.reporterNickname)}</a>
          ${v.targetMemberId ? ` · 대상: <a href="#/admin/members/${v.targetMemberId}">${escapeHtml(v.targetMemberNickname || `#${v.targetMemberId}`)}</a>` : ""}
          ${v.targetPostId ? ` · <a href="#/companion/${v.targetPostId}">모집글 #${v.targetPostId}</a>` : ""}
        </p>
        <p class="post-body">${escapeHtml(v.content)}</p>
        <label class="field"><span>답변 (회원에게 보여요)</span>
          <textarea name="answer" rows="3" maxlength="1000" placeholder="확인 결과와 조치 내용을 안내해 주세요.">${escapeHtml(v.answer || "")}</textarea>
        </label>
        <label class="field"><span>내부 메모 (회원에게 안 보여요)</span>
          <textarea name="adminMemo" rows="2" maxlength="1000">${escapeHtml(v.adminMemo || "")}</textarea>
        </label>
        <div class="row even">
          <select name="status">
            ${Object.entries(VOC_STATUS).map(([k, s]) => `<option value="${k}" ${v.status === k ? "selected" : ""}>${s.text}</option>`).join("")}
          </select>
          <button class="primary" type="submit">저장</button>
        </div>
        <p class="error inline" hidden></p>
      </form>`).join("") : `<p class="empty">해당하는 VOC가 없어요.</p>`}
  `;
  view.querySelectorAll(".voc-admin").forEach((form) => {
    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      const data = new FormData(form);
      const error = form.querySelector(".error");
      error.hidden = true;
      try {
        await api(`/api/admin/vocs/${form.dataset.id}`, {
          method: "PATCH",
          body: JSON.stringify({ status: data.get("status"), answer: data.get("answer"), adminMemo: data.get("adminMemo") }),
        });
        route();
      } catch (err) {
        if (handleAuthError(err)) return;
        showError(error, err.message);
      }
    });
  });
}

async function renderAdminActions(stats) {
  const list = await api("/api/admin/actions");
  const targetLink = (a) => {
    if (a.targetType === "MEMBER") return `<a href="#/admin/members/${a.targetId}">회원 #${a.targetId}</a>`;
    if (a.targetType === "POST") return `<a href="#/companion/${a.targetId}">모집글 #${a.targetId}</a>`;
    return `VOC #${a.targetId}`;
  };
  view.innerHTML = `
    ${adminNav("actions", stats)}
    <p class="hint">최근 100건 · 기록은 지워지지 않아요. 잘못 처리한 건은 <b>되돌리기</b>로 처리 전 상태를 복구할 수 있고, 되돌린 내역도 함께 남아요.
      같은 대상에 처리가 여러 번 있으면 최근 것부터 차례로 되돌릴 수 있어요.</p>
    ${list.length ? list.map((a) => actionEntry(a, targetLink(a))).join("") : `<p class="empty">아직 처리 내역이 없어요.</p>`}
  `;
  bindRevertButtons();
}

/* ---------- 개발용 문자함 ---------- */

function renderDevSms() {
  view.innerHTML = `
    <h1>개발용 문자함</h1>
    <p class="lead">개발 모드에서 보낸 인증 문자가 여기에 쌓여요. 실제 휴대폰으로는 가지 않아요. 3초마다 새로고침돼요.</p>
    <div id="sms-list" class="empty">불러오는 중이에요…</div>
  `;
  const list = document.getElementById("sms-list");
  const load = async () => {
    if (!document.body.contains(list)) return false;
    try {
      const messages = await api("/api/dev/sms");
      list.className = messages.length ? "sms-list" : "empty";
      list.innerHTML = messages.length
        ? messages.map((m) => `
          <div class="sms">
            <p class="meta">${escapeHtml(m.to)} · ${new Date(m.sentAt).toLocaleTimeString("ko-KR")}</p>
            <p class="sms-bubble">${escapeHtml(m.text)}</p>
          </div>`).join("")
        : "아직 보낸 문자가 없어요. 회원가입에서 인증번호를 받아 보세요.";
    } catch (err) {
      list.className = "error";
      list.textContent = err.status === 404
        ? "실제 문자 발송 모드라 개발용 문자함이 꺼져 있어요."
        : "문자함을 불러오지 못했어요.";
      return false;
    }
    return true;
  };
  load().then((ok) => {
    if (!ok) return;
    const timer = setInterval(async () => {
      if (!(await load())) clearInterval(timer);
    }, 3000);
  });
}

/* ---------- 배경 낙엽 ---------- */

const LEAF_SHAPES = [
  "M12 1l1.8 4.2 3.4-1.4-1 4.1 4.3-.8-2.3 3.4 3.3 1.6-4.2 1.4.9 2.4-3.6-.9L14 19h-1.2v4h-1.6v-4H10l-.6-3.9-3.6.9.9-2.4-4.2-1.4 3.3-1.6L3.5 8.1l4.3.8-1-4.1 3.4 1.4z",
  "M12 2C6.5 6.5 4 10.5 4 14.5A8 8 0 0 0 11.2 22.4V18l-3-3 1-1 2 2V9h1.6v7l2-2 1 1-3 3v4.4A8 8 0 0 0 20 14.5C20 10.5 17.5 6.5 12 2z",
];
const LEAF_COLORS = ["#d8602a", "#e8902f", "#b8441f", "#c9a227", "#9c3a22", "#e07a3f"];

function startLeaves() {
  const box = document.getElementById("leaves");
  if (!box || window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
  const count = window.innerWidth < 600 ? 10 : 18;
  const rand = (min, max) => min + Math.random() * (max - min);
  box.innerHTML = Array.from({ length: count }, () => {
    const size = rand(14, 30).toFixed(0);
    const style = [
      `left:${rand(-5, 100).toFixed(1)}%`,
      `--size:${size}px`,
      `--fall:${rand(11, 22).toFixed(1)}s`,
      `--delay:${rand(-22, 0).toFixed(1)}s`,
      `--drift:${rand(-120, 120).toFixed(0)}px`,
      `--spin:${rand(-540, 540).toFixed(0)}deg`,
      `--sway:${rand(2.5, 5).toFixed(1)}s`,
      `--alpha:${rand(0.45, 0.85).toFixed(2)}`,
      `color:${LEAF_COLORS[Math.floor(Math.random() * LEAF_COLORS.length)]}`,
    ].join(";");
    const shape = LEAF_SHAPES[Math.floor(Math.random() * LEAF_SHAPES.length)];
    return `<span class="leaf" style="${style}"><svg viewBox="0 0 24 24"><path d="${shape}"/></svg></span>`;
  }).join("");
}

/* ---------- 시작 ---------- */

startLeaves();
window.addEventListener("hashchange", route);
renderAccount();
loadMe().then(() => {
  renderAccount();
  route();
});
