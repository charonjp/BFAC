const config = window.APP_CONFIG ?? {};
const app = document.querySelector("#app");
const modalRoot = document.querySelector("#modal-root");

const ID_PATTERN = /^[A-Za-z0-9._-]{1,12}$/;
const ATTEMPTS = {
  1: {
    date: "first_date",
    memo: "first_memo",
    reaction: "first_reaction",
    label: "1回目",
  },
  2: {
    date: "second_date",
    memo: "second_memo",
    reaction: "second_reaction",
    label: "2回目",
  },
};
const STATUS_FILTERS = [
  { value: "", label: "すべて" },
  { value: "none", label: "未実施" },
  { value: "first", label: "1回完了" },
  { value: "second", label: "2回完了" },
];

let supabaseClient = null;
let touchGesture = null;
let horizontalScrollGesture = null;
let suppressHorizontalClick = false;

const state = {
  session: null,
  profile: null,
  phases: [],
  categories: [],
  households: [],
  children: [],
  foods: [],
  records: [],
  profiles: [],
  selectedChildId: null,
  phaseIndex: 0,
  categoryFilter: "",
  statusFilter: "",
  search: "",
  view: "main",
  settingsSection: "menu",
  foodScope: "household",
  adminHouseholdId: "",
  loading: false,
};

init();

window.addEventListener("error", (event) => {
  showToast(event.message || "ブラウザエラーが発生しました");
  event.preventDefault();
});

window.addEventListener("unhandledrejection", (event) => {
  showToast(event.reason?.message || "通信または処理中にエラーが発生しました");
  event.preventDefault();
});

async function init() {
  if (!config.SUPABASE_URL || !config.SUPABASE_ANON_KEY || config.SUPABASE_URL.includes("YOUR_")) {
    renderConfigMissing();
    return;
  }

  supabaseClient = window.supabase.createClient(config.SUPABASE_URL, config.SUPABASE_ANON_KEY);
  const { data } = await supabaseClient.auth.getSession();
  state.session = data.session;

  supabaseClient.auth.onAuthStateChange(async (_event, session) => {
    state.session = session;
    if (session) {
      await loadAll();
    } else {
      resetState();
      renderLogin();
    }
  });

  if (state.session) {
    await loadAll();
  } else {
    renderLogin();
  }
}

function resetState() {
  state.profile = null;
  state.phases = [];
  state.categories = [];
  state.households = [];
  state.children = [];
  state.foods = [];
  state.records = [];
  state.profiles = [];
  state.selectedChildId = null;
  state.phaseIndex = 0;
  state.categoryFilter = "";
  state.statusFilter = "";
  state.search = "";
  state.view = "main";
  state.settingsSection = "menu";
}

async function loadAll() {
  state.loading = true;
  renderLoading();
  try {
    await loadProfile();
    await Promise.all([loadMasters(), loadHouseholds(), loadChildren(), loadFoods()]);
    ensureSelectedChild();
    await loadRecords();
    if (isAdmin()) {
      await loadProfiles();
      state.foodScope = "global";
    }
    state.loading = false;
    render();
  } catch (error) {
    state.loading = false;
    showToast(error.message || "読み込みに失敗しました");
    renderLogin();
  }
}

async function loadProfile() {
  const { data, error } = await supabaseClient
    .from("profiles")
    .select("*, households(name)")
    .eq("id", state.session.user.id)
    .maybeSingle();
  if (error) throw error;
  if (!data) {
    await supabaseClient.auth.signOut();
    throw new Error("ログイン用ユーザは存在しますが、プロフィールが未作成です。WEB_DEPLOY.mdの「管理者ログインできない時の修復」を確認してください。");
  }
  if (!data.is_active) {
    await supabaseClient.auth.signOut();
    throw new Error("このユーザは停止されています");
  }
  state.profile = data;
  state.adminHouseholdId = data.household_id ?? "";
}

async function loadMasters() {
  const [phaseResult, categoryResult] = await Promise.all([
    supabaseClient.from("phases").select("*").eq("is_active", true).order("sort_order"),
    supabaseClient.from("categories").select("*").eq("is_active", true).order("sort_order"),
  ]);
  if (phaseResult.error) throw phaseResult.error;
  if (categoryResult.error) throw categoryResult.error;
  state.phases = phaseResult.data ?? [];
  state.categories = categoryResult.data ?? [];
}

async function loadHouseholds() {
  const { data, error } = await supabaseClient.from("households").select("*").order("created_at");
  if (error) throw error;
  state.households = data ?? [];
}

async function loadChildren() {
  const { data, error } = await supabaseClient
    .from("children")
    .select("*, households(name)")
    .order("household_id")
    .order("number");
  if (error) throw error;
  state.children = data ?? [];
}

async function loadFoods() {
  const { data, error } = await supabaseClient
    .from("foods")
    .select("*")
    .eq("is_active", true)
    .order("scope")
    .order("phase_code")
    .order("category_code")
    .order("serial");
  if (error) throw error;
  state.foods = data ?? [];
}

async function loadProfiles() {
  const { data, error } = await supabaseClient
    .from("profiles")
    .select("*, households(name)")
    .order("created_at");
  if (error) throw error;
  state.profiles = data ?? [];
}

async function loadRecords() {
  if (!state.selectedChildId) {
    state.records = [];
    return;
  }
  const { data, error } = await supabaseClient
    .from("food_records")
    .select("*")
    .eq("child_id", state.selectedChildId);
  if (error) throw error;
  state.records = data ?? [];
}

function ensureSelectedChild() {
  if (!state.children.length) {
    state.selectedChildId = null;
    return;
  }
  if (state.selectedChildId && state.children.some((child) => child.id === state.selectedChildId)) return;
  state.selectedChildId = state.children[0].id;
}

function isAdmin() {
  return state.profile?.role === "admin";
}

function selectedChild() {
  return state.children.find((child) => child.id === state.selectedChildId) ?? null;
}

function activePhase() {
  return state.phases[state.phaseIndex] ?? state.phases[0] ?? null;
}

function render() {
  if (!state.session || !state.profile) {
    renderLogin();
    return;
  }
  if (state.loading) {
    renderLoading();
    return;
  }

  clearAppHandlers();
  if (state.view === "settings") renderSettings();
  else if (state.view === "admin") renderAdmin();
  else renderMain();
}

function clearAppHandlers() {
  app.onclick = null;
  app.ontouchstart = null;
  app.ontouchend = null;
  touchGesture = null;
  horizontalScrollGesture = null;
  suppressHorizontalClick = false;
}

function runAsync(task) {
  Promise.resolve()
    .then(task)
    .catch((error) => {
      showToast(error?.message || "処理に失敗しました");
    });
}

function renderLoading() {
  clearAppHandlers();
  app.innerHTML = `
    <main class="login-panel">
      <h1>読み込み中</h1>
      <p class="lead">データを確認しています。</p>
    </main>
  `;
}

function renderConfigMissing() {
  clearAppHandlers();
  app.innerHTML = `
    <main class="login-panel">
      <h1>設定が必要です</h1>
      <p class="lead">docs/config.js に Supabase URL と anon key を設定してください。</p>
      <p class="message">サーバ設定手順は WEB_DEPLOY.md にまとめています。</p>
    </main>
  `;
}

function renderLogin() {
  clearAppHandlers();
  app.innerHTML = `
    <main class="login-panel">
      <h1>${escapeHtml(config.APP_NAME || "離乳食アレルギーチェック")}</h1>
      <p class="lead">管理者から発行されたIDとパスワードでログインします。</p>
      <form id="login-form">
        <label class="field">
          <span>ID（12文字以内、英数字と . _ -）</span>
          <input name="login_id" autocomplete="username" maxlength="12" required />
        </label>
        <label class="field">
          <span>パスワード</span>
          <input name="password" type="password" autocomplete="current-password" required />
        </label>
        <button class="primary-button" type="submit">ログイン</button>
      </form>
      <div id="login-message" class="message" aria-live="polite"></div>
    </main>
  `;

  document.querySelector("#login-form").addEventListener("submit", (event) => {
    event.preventDefault();
    runAsync(async () => {
      const form = new FormData(event.currentTarget);
      const loginId = String(form.get("login_id") || "").trim();
      const password = String(form.get("password") || "");
      const message = document.querySelector("#login-message");
      message.textContent = "";

      if (!ID_PATTERN.test(loginId)) {
        message.textContent = "IDは12文字以内の英数字と . _ - で入力してください。";
        return;
      }

      const { error } = await supabaseClient.auth.signInWithPassword({
        email: loginIdToEmail(loginId),
        password,
      });
      if (error) {
        message.textContent = "IDまたはパスワードが違います。";
      }
    });
  });
}

function renderMain() {
  const child = selectedChild();
  const phase = activePhase();

  app.innerHTML = `
    ${renderTopbar("離乳食チェック", "", "⚙", "settings")}
    <main class="content">
      ${
        child
          ? renderMainContent(child, phase)
          : `<section class="empty-panel settings-card">
              <h2>子供を登録してください</h2>
              <p>食材チェックを始めるには、子供マスタに最低1人の登録が必要です。</p>
              <button class="primary-button" data-action="open-child-create">子供を追加</button>
            </section>`
      }
    </main>
    ${renderBottomNav()}
  `;
  bindMainEvents();
}

function renderMainContent(child, phase) {
  const visibleFoods = filteredFoodsForChild(child);
  return `
    <section class="child-header">
      <label>子供</label>
      <select id="child-select">
        ${state.children
          .map((item) => {
            const household = item.households?.name ? ` / ${item.households.name}` : "";
            return `<option value="${item.id}" ${item.id === child.id ? "selected" : ""}>${escapeHtml(item.name)} (${escapeHtml(item.number)})${escapeHtml(household)}</option>`;
          })
          .join("")}
      </select>
    </section>
    <section class="status-filter-panel">
      <label for="status-filter-select">状態</label>
      <select id="status-filter-select" aria-label="状態フィルタ">
        ${STATUS_FILTERS.map((item) => (
          `<option value="${item.value}" ${state.statusFilter === item.value ? "selected" : ""}>${item.label}</option>`
        )).join("")}
      </select>
    </section>
    <div class="phase-title">${phase ? escapeHtml(phase.age_label) + "（" + escapeHtml(phase.name) + "）" : ""}</div>
    <nav class="tabs" aria-label="期">
      ${state.phases
        .map(
          (item, index) => `
          <button class="tab ${index === state.phaseIndex ? "active" : ""}" data-action="phase" data-index="${index}">
            ${escapeHtml(item.name)}
            <small>${escapeHtml(item.age_label)}</small>
          </button>
        `,
        )
        .join("")}
    </nav>
    <section class="toolbar">
      <div class="search-box">
        <input id="search-input" value="${escapeAttribute(state.search)}" placeholder="食材を検索" />
      </div>
      <div class="chips">
        <button class="chip ${!state.categoryFilter ? "active" : ""}" data-action="category" data-code="">すべて</button>
        ${state.categories
          .map(
            (category) => `
            <button class="chip ${state.categoryFilter === category.code ? "active" : ""}" data-action="category" data-code="${category.code}">
              ${escapeHtml(category.name)}
            </button>
          `,
          )
          .join("")}
      </div>
    </section>
    <section id="record-list" class="record-list">
      ${renderRecordList(visibleFoods)}
    </section>
  `;
}

function bindMainEvents() {
  document.querySelector("#child-select")?.addEventListener("change", (event) => {
    runAsync(async () => {
      state.selectedChildId = event.target.value;
      await loadRecords();
      render();
    });
  });
  document.querySelector("#status-filter-select")?.addEventListener("change", (event) => {
    state.statusFilter = event.target.value;
    updateRecordList();
  });
  document.querySelector("#search-input")?.addEventListener("input", (event) => {
    state.search = event.target.value;
    updateRecordList();
  });

  app.onclick = handleAppClick;
  bindHorizontalScrollGuards();
  bindPhaseSwipe();
}

function bindPhaseSwipe() {
  const recordList = document.querySelector("#record-list");
  if (!recordList) return;
  recordList.ontouchstart = (event) => {
    const touch = event.changedTouches[0];
    touchGesture = {
      x: touch.clientX,
      y: touch.clientY,
      ignorePhaseSwipe: shouldIgnorePhaseSwipe(event.target),
    };
  };
  recordList.ontouchend = (event) => {
    if (!touchGesture) return;
    if (touchGesture.ignorePhaseSwipe) {
      touchGesture = null;
      return;
    }
    const touch = event.changedTouches[0];
    const diffX = touch.clientX - touchGesture.x;
    const diffY = touch.clientY - touchGesture.y;
    touchGesture = null;
    if (Math.abs(diffX) > 80 && Math.abs(diffX) > Math.abs(diffY) * 1.4) {
      if (diffX < 0) changePhase(1);
      else changePhase(-1);
    }
  };
  recordList.ontouchcancel = () => {
    touchGesture = null;
  };
}

function bindHorizontalScrollGuards() {
  document.querySelectorAll(".chips, .tabs").forEach((scroller) => {
    scroller.addEventListener("touchstart", (event) => {
      const touch = event.changedTouches[0];
      horizontalScrollGesture = {
        x: touch.clientX,
        y: touch.clientY,
        moved: false,
      };
    }, { passive: true });
    scroller.addEventListener("touchmove", (event) => {
      if (!horizontalScrollGesture) return;
      const touch = event.changedTouches[0];
      const diffX = touch.clientX - horizontalScrollGesture.x;
      const diffY = touch.clientY - horizontalScrollGesture.y;
      if (Math.abs(diffX) > 10 && Math.abs(diffX) > Math.abs(diffY)) {
        horizontalScrollGesture.moved = true;
      }
    }, { passive: true });
    scroller.addEventListener("touchend", () => {
      if (horizontalScrollGesture?.moved) {
        suppressHorizontalClick = true;
        window.setTimeout(() => {
          suppressHorizontalClick = false;
        }, 360);
      }
      horizontalScrollGesture = null;
    }, { passive: true });
    scroller.addEventListener("touchcancel", () => {
      horizontalScrollGesture = null;
    }, { passive: true });
  });
}

function updateRecordList() {
  const child = selectedChild();
  const list = document.querySelector("#record-list");
  if (child && list) list.innerHTML = renderRecordList(filteredFoodsForChild(child));
}

function updateCategoryChips() {
  document.querySelectorAll(".chips .chip").forEach((chip) => {
    chip.classList.toggle("active", (chip.dataset.code || "") === state.categoryFilter);
  });
}

function handleAppClick(event) {
  const button = event.target.closest("[data-action]");
  if (!button) return;
  if (suppressHorizontalClick && button.closest(".chips, .tabs")) {
    event.preventDefault();
    event.stopPropagation();
    suppressHorizontalClick = false;
    return;
  }
  const action = button.dataset.action;
  runAsync(async () => {
    if (action === "settings") {
      state.view = "settings";
      state.settingsSection = "menu";
      render();
    } else if (action === "home") {
      state.view = "main";
      render();
    } else if (action === "admin") {
      state.view = "admin";
      render();
    } else if (action === "phase") {
      state.phaseIndex = Number(button.dataset.index);
      render();
    } else if (action === "category") {
      state.categoryFilter = button.dataset.code || "";
      updateCategoryChips();
      updateRecordList();
    } else if (action === "edit-date") {
      openDateModal(button.dataset.foodId, Number(button.dataset.attempt));
    } else if (action === "edit-memo") {
      openMemoModal(button.dataset.foodId, Number(button.dataset.attempt));
    } else if (action === "open-child-create") {
      openChildModal();
    } else if (action === "logout") {
      await supabaseClient.auth.signOut();
    }
  });
}

function shouldIgnorePhaseSwipe(target) {
  if (!(target instanceof Element)) return true;
  return Boolean(
    target.closest(
      ".chips, .tabs, .bottom-nav, .topbar, button, input, select, textarea, .date-button, .memo-button",
    ),
  );
}

function changePhase(direction) {
  const next = Math.min(Math.max(state.phaseIndex + direction, 0), state.phases.length - 1);
  if (next !== state.phaseIndex) {
    state.phaseIndex = next;
    render();
  }
}

function filteredFoodsForChild(child) {
  const phase = activePhase();
  const query = state.search.trim().toLowerCase();
  return state.foods
    .filter((food) => food.scope === "global" || food.household_id === child.household_id)
    .filter((food) => !phase || food.phase_code === phase.code)
    .filter((food) => !state.categoryFilter || food.category_code === state.categoryFilter)
    .filter((food) => recordMatchesStatus(recordFor(food.id), state.statusFilter))
    .filter((food) => !query || food.name.toLowerCase().includes(query) || food.code.includes(query))
    .sort(foodSorter);
}

function recordMatchesStatus(record, filter) {
  if (!filter) return true;
  const first = Boolean(record?.first_date);
  const second = Boolean(record?.second_date);
  if (filter === "none") return !first && !second;
  if (filter === "first") return first && !second;
  if (filter === "second") return second;
  return true;
}

function renderRecordList(foods) {
  return foods.length
    ? foods.map(renderFoodCard).join("")
    : `<div class="empty-panel settings-card"><p>該当する食材がありません。</p></div>`;
}

function foodSorter(a, b) {
  return (
    a.phase_code.localeCompare(b.phase_code) ||
    a.category_code.localeCompare(b.category_code) ||
    a.serial - b.serial ||
    a.name.localeCompare(b.name, "ja")
  );
}

function renderFoodCard(food) {
  const record = recordFor(food.id);
  const status = getStatus(record);
  return `
    <article class="record-card ${status.className}">
      <div class="record-head">
        <div>
          <div class="category-label">${escapeHtml(categoryLabel(food.category_code))}</div>
          <div class="food-name">${escapeHtml(food.name)}</div>
          <div class="food-code">${escapeHtml(food.code)} ${food.scope === "household" ? "家庭用" : "共通"}</div>
        </div>
        <span class="status-badge">${escapeHtml(status.label)}</span>
      </div>
      <div class="attempt-row">
        ${renderAttempt(food.id, record, 1)}
        ${renderAttempt(food.id, record, 2)}
      </div>
    </article>
  `;
}

function renderAttempt(foodId, record, attempt) {
  const spec = ATTEMPTS[attempt];
  const date = record?.[spec.date] || "";
  const memo = record?.[spec.memo] || "";
  const reaction = record?.[spec.reaction] === true;
  return `
    <div class="attempt">
      <div class="attempt-title">${spec.label}</div>
      <div class="attempt-actions">
        <button class="date-button" data-action="edit-date" data-food-id="${foodId}" data-attempt="${attempt}">
          ${date ? escapeHtml(formatShortDate(date)) : "日付"}
        </button>
        <button class="memo-button ${reaction ? "has-reaction" : memo ? "has-memo" : ""}" data-action="edit-memo" data-food-id="${foodId}" data-attempt="${attempt}" aria-label="${spec.label}メモ">
          ✎
        </button>
      </div>
    </div>
  `;
}

function renderSettings() {
  const title =
    state.settingsSection === "menu"
      ? "設定"
      : state.settingsSection === "children"
        ? "子供マスタ"
        : state.settingsSection === "masters"
          ? "期・分類マスタ"
          : "食材マスタ";
  app.innerHTML = `
    ${renderTopbar(title, "‹", "", "")}
    <main class="content">
      ${state.settingsSection === "menu" ? renderSettingsMenu() : ""}
      ${state.settingsSection === "children" ? renderChildMaster() : ""}
      ${state.settingsSection === "masters" ? renderPhaseCategoryMaster() : ""}
      ${state.settingsSection === "foods" ? renderFoodMaster() : ""}
    </main>
    ${renderBottomNav()}
  `;
  bindSettingsEvents();
}

function renderSettingsMenu() {
  return `
    <section class="settings-grid">
      <button class="settings-card" data-action="settings-children">
        <h2>子供マスタ</h2>
        <p>子供の追加、名前修正、削除を行います。</p>
      </button>
      <button class="settings-card" data-action="settings-foods">
        <h2>食材マスタ</h2>
        <p>${isAdmin() ? "共通マスタと家庭ごとの食材を編集します。" : "家庭ごとの食材を追加、修正します。"}</p>
      </button>
      ${
        isAdmin()
          ? `<button class="settings-card" data-action="settings-masters">
              <h2>期・分類マスタ</h2>
              <p>初期、中期、後期、完了期と分類名、表示順を編集します。</p>
            </button>`
          : ""
      }
      <button class="settings-card" data-action="export-pdf">
        <h2>PDFに出力</h2>
        <p>選択中の子供について、現在までの内容をPDFとして保存します。</p>
      </button>
      <button class="settings-card" data-action="export-csv">
        <h2>CSVに出力</h2>
        <p>選択中の子供について、記録とメモをCSVとして保存します。</p>
      </button>
    </section>
  `;
}

function renderChildMaster() {
  return `
    <button class="primary-button" data-action="create-child">子供を追加</button>
    <section class="master-list">
      ${state.children.map(renderChildItem).join("") || `<div class="empty-panel settings-card"><p>子供が登録されていません。</p></div>`}
    </section>
  `;
}

function renderChildItem(child) {
  const household = child.households?.name ? ` / ${child.households.name}` : "";
  return `
    <article class="master-item">
      <div>
        <strong>${escapeHtml(child.name)}</strong>
        <small>番号 ${escapeHtml(child.number)}${escapeHtml(household)}</small>
      </div>
      <div>
        <button class="icon-button" data-action="edit-child" data-id="${child.id}" aria-label="編集">✎</button>
        <button class="icon-button" data-action="delete-child" data-id="${child.id}" aria-label="削除">×</button>
      </div>
    </article>
  `;
}

function renderPhaseCategoryMaster() {
  if (!isAdmin()) {
    return `<section class="empty-panel settings-card"><p>管理者のみ使用できます。</p></section>`;
  }
  return `
    <section class="settings-grid">
      <div class="settings-card">
        <h2>期</h2>
        <p>例: 初期、中期、後期、完了期。コードは2桁です。</p>
        <button class="secondary-button" data-action="create-phase" style="margin-top: 10px;">期を追加</button>
        <div class="master-list">
          ${state.phases.map(renderPhaseItem).join("")}
        </div>
      </div>
      <div class="settings-card">
        <h2>分類</h2>
        <p>例: 穀類、野菜、果物。コードは2桁です。</p>
        <button class="secondary-button" data-action="create-category-master" style="margin-top: 10px;">分類を追加</button>
        <div class="master-list">
          ${state.categories.map(renderCategoryItem).join("")}
        </div>
      </div>
    </section>
  `;
}

function renderPhaseItem(phase) {
  return `
    <article class="master-item">
      <div>
        <strong>${escapeHtml(phase.age_label)}（${escapeHtml(phase.name)}）</strong>
        <small>コード ${escapeHtml(phase.code)} / 表示順 ${phase.sort_order}</small>
      </div>
      <button class="icon-button" data-action="edit-phase" data-code="${phase.code}" aria-label="編集">✎</button>
    </article>
  `;
}

function renderCategoryItem(category) {
  return `
    <article class="master-item">
      <div>
        <strong>${escapeHtml(category.name)}</strong>
        <small>コード ${escapeHtml(category.code)} / 表示順 ${category.sort_order}</small>
      </div>
      <button class="icon-button" data-action="edit-category-master" data-code="${category.code}" aria-label="編集">✎</button>
    </article>
  `;
}

function renderFoodMaster() {
  const targetHouseholdId = foodMasterHouseholdId();
  const foods = state.foods
    .filter((food) => {
      if (state.foodScope === "global") return food.scope === "global";
      return food.scope === "household" && food.household_id === targetHouseholdId;
    })
    .sort(foodSorter);

  return `
    <div class="settings-grid">
      ${
        isAdmin()
          ? `<label class="field">
              <span>編集対象</span>
              <select id="food-scope-select">
                <option value="global" ${state.foodScope === "global" ? "selected" : ""}>共通食材マスタ</option>
                <option value="household" ${state.foodScope === "household" ? "selected" : ""}>家庭ごとの食材</option>
              </select>
            </label>
            ${
              state.foodScope === "household"
                ? `<label class="field">
                    <span>家庭</span>
                    <select id="food-household-select">
                      ${state.households.map((household) => `<option value="${household.id}" ${household.id === state.adminHouseholdId ? "selected" : ""}>${escapeHtml(household.name)}</option>`).join("")}
                    </select>
                  </label>`
                : ""
            }`
          : ""
      }
      <button class="primary-button" data-action="create-food">新規登録</button>
      <section class="master-list">
        ${foods.map(renderFoodMasterItem).join("") || `<div class="empty-panel settings-card"><p>食材がありません。</p></div>`}
      </section>
    </div>
  `;
}

function renderFoodMasterItem(food) {
  return `
    <article class="master-item">
      <div>
        <strong>${escapeHtml(food.name)}</strong>
        <small>${escapeHtml(phaseLabel(food.phase_code))} / ${escapeHtml(categoryLabel(food.category_code))} / ${escapeHtml(food.code)}</small>
      </div>
      <button class="icon-button" data-action="edit-food" data-id="${food.id}" aria-label="編集">✎</button>
    </article>
  `;
}

function bindSettingsEvents() {
  document.querySelector("#food-scope-select")?.addEventListener("change", (event) => {
    state.foodScope = event.target.value;
    render();
  });
  document.querySelector("#food-household-select")?.addEventListener("change", (event) => {
    state.adminHouseholdId = event.target.value;
    render();
  });

  app.onclick = (event) => {
    const button = event.target.closest("[data-action]");
    if (!button) return;
    const action = button.dataset.action;
    runAsync(async () => {
      if (action === "home") {
        state.view = "main";
        render();
      } else if (action === "settings") {
        state.settingsSection = "menu";
        render();
      } else if (action === "settings-children") {
        state.settingsSection = "children";
        render();
      } else if (action === "settings-foods") {
        state.settingsSection = "foods";
        render();
      } else if (action === "settings-masters") {
        state.settingsSection = "masters";
        render();
      } else if (action === "create-child") {
        openChildModal();
      } else if (action === "edit-child") {
        openChildModal(state.children.find((child) => child.id === button.dataset.id));
      } else if (action === "delete-child") {
        await deleteChild(button.dataset.id);
      } else if (action === "create-food") {
        openFoodModal();
      } else if (action === "edit-food") {
        openFoodModal(state.foods.find((food) => food.id === button.dataset.id));
      } else if (action === "create-phase") {
        openPhaseModal();
      } else if (action === "edit-phase") {
        openPhaseModal(state.phases.find((phase) => phase.code === button.dataset.code));
      } else if (action === "create-category-master") {
        openCategoryMasterModal();
      } else if (action === "edit-category-master") {
        openCategoryMasterModal(state.categories.find((category) => category.code === button.dataset.code));
      } else if (action === "export-csv") {
        exportCsv();
      } else if (action === "export-pdf") {
        await exportPdf();
      } else if (action === "admin") {
        state.view = "admin";
        render();
      } else if (action === "logout") {
        await supabaseClient.auth.signOut();
      }
    });
  };
}

function openPhaseModal(phase = null) {
  showModal(`
    <h2>${phase ? "期を修正" : "期を追加"}</h2>
    <label class="field">
      <span>コード（2桁）</span>
      <input id="phase-code" value="${escapeAttribute(phase?.code || "")}" maxlength="2" ${phase ? "disabled" : ""} />
    </label>
    <label class="field">
      <span>名称</span>
      <input id="phase-name" value="${escapeAttribute(phase?.name || "")}" placeholder="例: 初期" />
    </label>
    <label class="field">
      <span>月齢表示</span>
      <input id="phase-age" value="${escapeAttribute(phase?.age_label || "")}" placeholder="例: 5～6か月" />
    </label>
    <label class="field">
      <span>表示順</span>
      <input id="phase-order" type="number" value="${phase?.sort_order ?? state.phases.length + 1}" />
    </label>
    <div class="modal-actions">
      <button class="secondary-button" data-modal-action="cancel">キャンセル</button>
      <button class="primary-button" data-modal-action="save">保存</button>
    </div>
  `, (modal) => {
    modal.querySelector('[data-modal-action="cancel"]').addEventListener("click", closeModal);
    modal.querySelector('[data-modal-action="save"]').addEventListener("click", async () => {
      const payload = {
        code: modal.querySelector("#phase-code").value.trim(),
        name: modal.querySelector("#phase-name").value.trim(),
        age_label: modal.querySelector("#phase-age").value.trim(),
        sort_order: Number(modal.querySelector("#phase-order").value || 0),
        is_active: true,
      };
      if (!/^[0-9]{2}$/.test(payload.code) || !payload.name || !payload.age_label || !payload.sort_order) {
        showToast("コード、名称、月齢、表示順を入力してください");
        return;
      }
      const result = phase
        ? await supabaseClient.from("phases").update(payload).eq("code", phase.code)
        : await supabaseClient.from("phases").insert(payload);
      if (result.error) {
        showToast(result.error.message);
        return;
      }
      closeModal();
      await loadMasters();
      render();
    });
  });
}

function openCategoryMasterModal(category = null) {
  showModal(`
    <h2>${category ? "分類を修正" : "分類を追加"}</h2>
    <label class="field">
      <span>コード（2桁）</span>
      <input id="category-code" value="${escapeAttribute(category?.code || "")}" maxlength="2" ${category ? "disabled" : ""} />
    </label>
    <label class="field">
      <span>名称</span>
      <input id="category-name" value="${escapeAttribute(category?.name || "")}" placeholder="例: 野菜" />
    </label>
    <label class="field">
      <span>表示順</span>
      <input id="category-order" type="number" value="${category?.sort_order ?? state.categories.length + 1}" />
    </label>
    <div class="modal-actions">
      <button class="secondary-button" data-modal-action="cancel">キャンセル</button>
      <button class="primary-button" data-modal-action="save">保存</button>
    </div>
  `, (modal) => {
    modal.querySelector('[data-modal-action="cancel"]').addEventListener("click", closeModal);
    modal.querySelector('[data-modal-action="save"]').addEventListener("click", async () => {
      const payload = {
        code: modal.querySelector("#category-code").value.trim(),
        name: modal.querySelector("#category-name").value.trim(),
        sort_order: Number(modal.querySelector("#category-order").value || 0),
        is_active: true,
      };
      if (!/^[0-9]{2}$/.test(payload.code) || !payload.name || !payload.sort_order) {
        showToast("コード、名称、表示順を入力してください");
        return;
      }
      const result = category
        ? await supabaseClient.from("categories").update(payload).eq("code", category.code)
        : await supabaseClient.from("categories").insert(payload);
      if (result.error) {
        showToast(result.error.message);
        return;
      }
      closeModal();
      await loadMasters();
      render();
    });
  });
}

function renderAdmin() {
  app.innerHTML = `
    ${renderTopbar("管理者", "‹", "", "")}
    <main class="content">
      <section class="admin-panel">
        <h2>ユーザ追加</h2>
        <p>IDは12文字以内、使える記号は . _ - です。</p>
        <form id="admin-user-form">
          <label class="field"><span>ID</span><input name="login_id" maxlength="12" required /></label>
          <label class="field"><span>初期パスワード</span><input name="password" type="password" required /></label>
          <label class="field"><span>表示名</span><input name="display_name" required /></label>
          <label class="field"><span>家庭名</span><input name="household_name" placeholder="例: 高木家" required /></label>
          <button class="primary-button" type="submit">ユーザを追加</button>
        </form>
      </section>
      <section class="admin-panel" style="margin-top: 12px;">
        <h2>ユーザ一覧</h2>
        <div class="master-list">
          ${state.profiles.map(renderProfileItem).join("") || "<p>ユーザがありません。</p>"}
        </div>
      </section>
    </main>
    ${renderBottomNav()}
  `;

  document.querySelector("#admin-user-form")?.addEventListener("submit", (event) => {
    event.preventDefault();
    const formElement = event.currentTarget;
    runAsync(async () => {
      const form = new FormData(formElement);
      const payload = {
        login_id: String(form.get("login_id") || "").trim(),
        password: String(form.get("password") || ""),
        display_name: String(form.get("display_name") || "").trim(),
        household_name: String(form.get("household_name") || "").trim(),
      };
      if (!ID_PATTERN.test(payload.login_id)) {
        showToast("IDは12文字以内の英数字と . _ - で入力してください。");
        return;
      }
      const { error } = await supabaseClient.functions.invoke("admin-create-user", { body: payload });
      if (error) {
        showToast(error.message || "ユーザ追加に失敗しました");
        return;
      }
      showToast("ユーザを追加しました");
      await Promise.all([loadHouseholds(), loadChildren(), loadProfiles()]);
      formElement.reset();
      render();
    });
  });

  app.onclick = (event) => {
    const button = event.target.closest("[data-action]");
    if (!button) return;
    runAsync(async () => {
      if (button.dataset.action === "home") {
        state.view = "main";
        render();
      } else if (button.dataset.action === "settings") {
        state.view = "settings";
        state.settingsSection = "menu";
        render();
      } else if (button.dataset.action === "logout") {
        await supabaseClient.auth.signOut();
      }
    });
  };
}

function renderProfileItem(profile) {
  return `
    <article class="master-item">
      <div>
        <strong>${escapeHtml(profile.display_name || profile.login_id)}</strong>
        <small>${escapeHtml(profile.login_id)} / ${profile.role === "admin" ? "管理者" : "ユーザ"} / ${escapeHtml(profile.households?.name || "")}</small>
      </div>
    </article>
  `;
}

function renderTopbar(title, left, right, rightAction) {
  const leftButton = left
    ? `<button class="icon-button" data-action="home" aria-label="戻る">${left}</button>`
    : `<span></span>`;
  const rightButton = right
    ? `<button class="icon-button" data-action="${rightAction}" aria-label="設定">${right}</button>`
    : `<span></span>`;
  return `
    <header class="topbar">
      ${leftButton}
      <h1>${escapeHtml(title)}</h1>
      ${rightButton}
    </header>
  `;
}

function renderBottomNav() {
  return `
    <nav class="bottom-nav">
      <div class="bottom-nav-inner">
        <button class="nav-button ${state.view === "main" ? "active" : ""}" data-action="home">トップ</button>
        <button class="nav-button ${state.view === "settings" ? "active" : ""}" data-action="settings">設定</button>
        ${isAdmin() ? `<button class="nav-button ${state.view === "admin" ? "active" : ""}" data-action="admin">管理</button>` : `<span></span>`}
        <button class="nav-button" data-action="logout">ログアウト</button>
      </div>
    </nav>
  `;
}

async function openDateModal(foodId, attempt) {
  const spec = ATTEMPTS[attempt];
  const record = recordFor(foodId);
  const current = record?.[spec.date] || todayIso();
  showModal(`
    <h2>${spec.label}の日付</h2>
    <label class="field">
      <span>日付</span>
      <input id="date-value" type="date" value="${current}" />
    </label>
    <div class="modal-actions">
      <button class="secondary-button" data-modal-action="clear">クリア</button>
      <button class="primary-button" data-modal-action="save">保存</button>
    </div>
  `, async (modal) => {
    modal.querySelector('[data-modal-action="save"]').addEventListener("click", async () => {
      await saveRecord(foodId, { [spec.date]: modal.querySelector("#date-value").value || null });
      closeModal();
      showToast("日付を保存しました");
    });
    modal.querySelector('[data-modal-action="clear"]').addEventListener("click", async () => {
      await saveRecord(foodId, { [spec.date]: null });
      closeModal();
      showToast("日付をクリアしました");
    });
  });
}

function openMemoModal(foodId, attempt) {
  const spec = ATTEMPTS[attempt];
  const food = state.foods.find((item) => item.id === foodId);
  const record = recordFor(foodId);
  const memo = record?.[spec.memo] || "";
  const reaction = record?.[spec.reaction] === true;

  showModal(`
    <h2>${escapeHtml(food?.name || "")} ${spec.label}メモ</h2>
    <label class="field">
      <span>反応あり</span>
      <select id="reaction-value">
        <option value="false" ${!reaction ? "selected" : ""}>なし</option>
        <option value="true" ${reaction ? "selected" : ""}>あり</option>
      </select>
    </label>
    <label class="field">
      <span>メモ</span>
      <textarea id="memo-value" placeholder="発疹、下痢、嘔吐、機嫌、医師相談など">${escapeHtml(memo)}</textarea>
    </label>
    <div class="modal-actions">
      <button class="secondary-button" data-modal-action="cancel">キャンセル</button>
      <button class="primary-button" data-modal-action="save">保存</button>
    </div>
  `, (modal) => {
    modal.querySelector('[data-modal-action="cancel"]').addEventListener("click", closeModal);
    modal.querySelector('[data-modal-action="save"]').addEventListener("click", async () => {
      await saveRecord(foodId, {
        [spec.memo]: modal.querySelector("#memo-value").value.trim(),
        [spec.reaction]: modal.querySelector("#reaction-value").value === "true",
      });
      closeModal();
      showToast("メモを保存しました");
    });
  });
}

async function saveRecord(foodId, patch) {
  const child = selectedChild();
  if (!child) return;
  const current = recordFor(foodId) || {
    child_id: child.id,
    food_id: foodId,
    first_date: null,
    second_date: null,
    first_memo: "",
    second_memo: "",
    first_reaction: false,
    second_reaction: false,
  };
  const payload = {
    ...current,
    ...patch,
    child_id: child.id,
    food_id: foodId,
    updated_at: new Date().toISOString(),
  };
  const { error } = await supabaseClient.from("food_records").upsert(payload, { onConflict: "child_id,food_id" });
  if (error) {
    showToast(error.message);
    return;
  }
  await loadRecords();
  render();
}

function openChildModal(child = null) {
  const householdOptions = isAdmin()
    ? `<label class="field">
        <span>家庭</span>
        <select id="child-household">
          ${state.households.map((household) => `<option value="${household.id}" ${household.id === (child?.household_id || state.adminHouseholdId) ? "selected" : ""}>${escapeHtml(household.name)}</option>`).join("")}
        </select>
      </label>`
    : "";
  showModal(`
    <h2>${child ? "子供を修正" : "子供を追加"}</h2>
    ${householdOptions}
    <label class="field">
      <span>名前</span>
      <input id="child-name" value="${escapeAttribute(child?.name || "")}" />
    </label>
    <div class="modal-actions">
      <button class="secondary-button" data-modal-action="cancel">キャンセル</button>
      <button class="primary-button" data-modal-action="save">保存</button>
    </div>
  `, (modal) => {
    modal.querySelector('[data-modal-action="cancel"]').addEventListener("click", closeModal);
    modal.querySelector('[data-modal-action="save"]').addEventListener("click", async () => {
      const name = modal.querySelector("#child-name").value.trim();
      const householdId = isAdmin()
        ? modal.querySelector("#child-household")?.value
        : state.profile.household_id;
      if (!name || !householdId) {
        showToast("名前を入力してください");
        return;
      }
      if (child) {
        const { error } = await supabaseClient.from("children").update({ name }).eq("id", child.id);
        if (error) showToast(error.message);
      } else {
        const number = nextChildNumber(householdId);
        const { error } = await supabaseClient.from("children").insert({ household_id: householdId, number, name });
        if (error) showToast(error.message);
      }
      closeModal();
      await loadChildren();
      ensureSelectedChild();
      await loadRecords();
      render();
    });
  });
}

async function deleteChild(childId) {
  const child = state.children.find((item) => item.id === childId);
  if (!child) return;
  const siblings = state.children.filter((item) => item.household_id === child.household_id);
  if (siblings.length <= 1) {
    showToast("最低1人の登録が必要です");
    return;
  }
  if (!window.confirm(`${child.name} を削除しますか？記録も削除されます。`)) return;
  await supabaseClient.from("food_records").delete().eq("child_id", childId);
  const { error } = await supabaseClient.from("children").delete().eq("id", childId);
  if (error) showToast(error.message);
  await loadChildren();
  ensureSelectedChild();
  await loadRecords();
  render();
}

function openFoodModal(food = null) {
  const scope = food?.scope || state.foodScope || "household";
  const householdId = food?.household_id || foodMasterHouseholdId();
  showModal(`
    <h2>${food ? "食材を修正" : "食材を新規登録"}</h2>
    <label class="field">
      <span>期</span>
      <select id="food-phase">
        ${state.phases.map((phase) => `<option value="${phase.code}" ${phase.code === (food?.phase_code || state.phases[0]?.code) ? "selected" : ""}>${escapeHtml(phase.age_label)}（${escapeHtml(phase.name)}）</option>`).join("")}
      </select>
    </label>
    <label class="field">
      <span>分類</span>
      <select id="food-category">
        ${state.categories.map((category) => `<option value="${category.code}" ${category.code === (food?.category_code || state.categories[0]?.code) ? "selected" : ""}>${escapeHtml(category.name)}</option>`).join("")}
      </select>
    </label>
    <label class="field">
      <span>食材名</span>
      <input id="food-name" value="${escapeAttribute(food?.name || "")}" />
    </label>
    ${food ? `<p class="lead">現在のコード: ${escapeHtml(food.code)}</p>` : ""}
    <div class="modal-actions">
      <button class="secondary-button" data-modal-action="cancel">キャンセル</button>
      <button class="primary-button" data-modal-action="save">${food ? "保存" : "登録"}</button>
    </div>
  `, (modal) => {
    modal.querySelector('[data-modal-action="cancel"]').addEventListener("click", closeModal);
    modal.querySelector('[data-modal-action="save"]').addEventListener("click", async () => {
      const phaseCode = modal.querySelector("#food-phase").value;
      const categoryCode = modal.querySelector("#food-category").value;
      const name = modal.querySelector("#food-name").value.trim();
      if (!name) {
        showToast("食材名を入力してください");
        return;
      }

      const scopeForSave = isAdmin() && scope === "global" ? "global" : "household";
      const householdForSave = scopeForSave === "global" ? null : householdId;
      let serial = food?.serial;
      let code = food?.code;
      if (!food || food.phase_code !== phaseCode || food.category_code !== categoryCode || food.scope !== scopeForSave || food.household_id !== householdForSave) {
        serial = nextFoodSerial(scopeForSave, householdForSave, phaseCode, categoryCode);
        code = `${phaseCode}${categoryCode}${String(serial).padStart(3, "0")}`;
      }

      const payload = {
        scope: scopeForSave,
        household_id: householdForSave,
        phase_code: phaseCode,
        category_code: categoryCode,
        serial,
        code,
        name,
        is_active: true,
      };
      const result = food
        ? await supabaseClient.from("foods").update(payload).eq("id", food.id)
        : await supabaseClient.from("foods").insert(payload);
      if (result.error) {
        showToast(result.error.message);
        return;
      }
      closeModal();
      await loadFoods();
      render();
    });
  });
}

function foodMasterHouseholdId() {
  if (isAdmin()) return state.adminHouseholdId || state.households[0]?.id || "";
  return state.profile.household_id;
}

function nextFoodSerial(scope, householdId, phaseCode, categoryCode) {
  const serials = state.foods
    .filter((food) => food.scope === scope)
    .filter((food) => (scope === "global" ? !food.household_id : food.household_id === householdId))
    .filter((food) => food.phase_code === phaseCode && food.category_code === categoryCode)
    .map((food) => food.serial);
  return Math.max(0, ...serials) + 1;
}

function nextChildNumber(householdId) {
  const used = new Set(
    state.children
      .filter((child) => child.household_id === householdId)
      .map((child) => Number(child.number)),
  );
  for (let i = 1; i <= 99; i += 1) {
    if (!used.has(i)) return String(i).padStart(2, "0");
  }
  return "99";
}

function recordFor(foodId) {
  return state.records.find((record) => record.food_id === foodId) ?? null;
}

function getStatus(record) {
  const first = Boolean(record?.first_date);
  const second = Boolean(record?.second_date);
  const reaction = record?.first_reaction === true || record?.second_reaction === true;
  const base = !first && !second ? "未実施" : first && !second ? "1回目済" : !first && second ? "2回目済" : "完了";
  if (reaction) return { label: `${base}・反応あり`, className: "status-reaction" };
  if (!first && !second) return { label: base, className: "status-none" };
  if (first && !second) return { label: base, className: "status-first" };
  if (!first && second) return { label: base, className: "status-second" };
  return { label: base, className: "status-done" };
}

function phaseLabel(code) {
  const phase = state.phases.find((item) => item.code === code);
  return phase ? `${phase.age_label}（${phase.name}）` : code;
}

function categoryLabel(code) {
  return state.categories.find((item) => item.code === code)?.name ?? code;
}

function buildExportRows() {
  const child = selectedChild();
  if (!child) return [];
  return state.foods
    .filter((food) => food.scope === "global" || food.household_id === child.household_id)
    .sort(foodSorter)
    .map((food) => ({ food, record: recordFor(food.id) }));
}

function exportCsv() {
  const child = selectedChild();
  if (!child) {
    showToast("子供を選択してください");
    return;
  }
  const rows = [
    ["子供名", child.name],
    ["出力日", todayIso()],
    [],
    ["期", "分類", "食材コード", "食材", "状況", "1回目日付", "1回目反応", "1回目メモ", "2回目日付", "2回目反応", "2回目メモ"],
  ];
  buildExportRows().forEach(({ food, record }) => {
    rows.push([
      phaseLabel(food.phase_code),
      categoryLabel(food.category_code),
      food.code,
      food.name,
      getStatus(record).label,
      record?.first_date || "",
      record?.first_reaction ? "反応あり" : "",
      record?.first_memo || "",
      record?.second_date || "",
      record?.second_reaction ? "反応あり" : "",
      record?.second_memo || "",
    ]);
  });
  const csv = "\uFEFF" + rows.map((row) => row.map(csvCell).join(",")).join("\n");
  downloadBlob(csv, `baby_allergy_${safeFileName(child.name)}_${todayIso()}.csv`, "text/csv;charset=utf-8");
}

async function exportPdf() {
  if (!window.html2canvas || !window.jspdf) {
    showToast("PDFライブラリを読み込めませんでした。通信状況を確認してください。");
    return;
  }
  const child = selectedChild();
  if (!child) {
    showToast("子供を選択してください");
    return;
  }
  const container = document.createElement("div");
  container.className = "hidden-render";
  container.innerHTML = renderReport(child);
  document.body.appendChild(container);

  try {
    const paper = container.querySelector(".report-paper");
    const canvas = await window.html2canvas(paper, { scale: 2, backgroundColor: "#ffffff" });
    const image = canvas.toDataURL("image/png");
    const pdf = new window.jspdf.jsPDF("p", "mm", "a4");
    const pageWidth = 210;
    const pageHeight = 297;
    const printMargin = 12;
    const printableWidth = pageWidth - printMargin * 2;
    const printableHeight = pageHeight - printMargin * 2;
    const imageHeight = (canvas.height * printableWidth) / canvas.width;
    let heightLeft = imageHeight;
    let position = printMargin;

    pdf.addImage(image, "PNG", printMargin, position, printableWidth, imageHeight);
    heightLeft -= printableHeight;
    while (heightLeft > 0) {
      position -= printableHeight;
      pdf.addPage();
      pdf.addImage(image, "PNG", printMargin, position, printableWidth, imageHeight);
      heightLeft -= printableHeight;
    }
    pdf.save(`baby_allergy_${safeFileName(child.name)}_${todayIso()}.pdf`);
  } finally {
    container.remove();
  }
}

function renderReport(child) {
  const rows = buildExportRows();
  return `
    <section class="report-paper">
      <h1>離乳食アレルギーチェック</h1>
      <p>子供名: ${escapeHtml(child.name)}　出力日: ${todayIso()}</p>
      ${state.phases
        .map((phase) => {
          const phaseRows = rows.filter(({ food }) => food.phase_code === phase.code);
          if (!phaseRows.length) return "";
          return `
            <h2>${escapeHtml(phase.age_label)}（${escapeHtml(phase.name)}）</h2>
            <table>
              <thead><tr><th>分類</th><th>食材</th><th>1回目</th><th>1回目メモ</th><th>2回目</th><th>2回目メモ</th></tr></thead>
              <tbody>
                ${phaseRows
                  .map(({ food, record }) => `
                    <tr>
                      <td>${escapeHtml(categoryLabel(food.category_code))}</td>
                      <td>${escapeHtml(food.name)}</td>
                      <td>${escapeHtml(record?.first_date || "")}</td>
                      <td>${escapeHtml(reportMemo(record?.first_memo, record?.first_reaction))}</td>
                      <td>${escapeHtml(record?.second_date || "")}</td>
                      <td>${escapeHtml(reportMemo(record?.second_memo, record?.second_reaction))}</td>
                    </tr>
                  `)
                  .join("")}
              </tbody>
            </table>
          `;
        })
        .join("")}
    </section>
  `;
}

function reportMemo(memo, reaction) {
  const clean = memo || "";
  if (reaction && clean) return `反応あり: ${clean}`;
  if (reaction) return "反応あり";
  return clean;
}

function showModal(html, bind) {
  modalRoot.innerHTML = `
    <div class="modal-backdrop">
      <section class="modal-card" role="dialog" aria-modal="true">
        ${html}
      </section>
    </div>
  `;
  const backdrop = modalRoot.querySelector(".modal-backdrop");
  const card = modalRoot.querySelector(".modal-card");
  backdrop.addEventListener("click", (event) => {
    if (event.target === backdrop) closeModal();
  });
  bind?.(card);
}

function closeModal() {
  modalRoot.innerHTML = "";
}

function showToast(message) {
  const current = document.querySelector(".toast");
  current?.remove();
  const toast = document.createElement("div");
  toast.className = "toast";
  toast.textContent = message;
  document.body.appendChild(toast);
  window.setTimeout(() => toast.remove(), 3400);
}

function loginIdToEmail(loginId) {
  const bytes = new TextEncoder().encode(loginId);
  let binary = "";
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte);
  });
  const encoded = btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
  return `u-${encoded}@baby-allergy.local`;
}

function todayIso() {
  return new Date().toISOString().slice(0, 10);
}

function formatShortDate(value) {
  const [, month, day] = value.split("-");
  return `${Number(month)}/${Number(day)}`;
}

function csvCell(value) {
  return `"${String(value ?? "").replaceAll('"', '""').replaceAll("\n", " ")}"`;
}

function downloadBlob(content, fileName, type) {
  const blob = new Blob([content], { type });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = fileName;
  anchor.click();
  URL.revokeObjectURL(url);
}

function safeFileName(value) {
  return String(value || "child").replace(/[\\/:*?"<>|]/g, "_");
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function escapeAttribute(value) {
  return escapeHtml(value).replaceAll("\n", " ");
}
