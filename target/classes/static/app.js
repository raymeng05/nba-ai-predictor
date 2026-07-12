const API_URL = "/api/player";
const PREDICTION_URL = "/api/prediction";

const numericFields = [
  "age",
  "games",
  "gamesStarted",
  "minutesPlayed",
  "fieldGoals",
  "fieldGoalsAttempted",
  "fieldGoalPct",
  "threePointers",
  "threePointersAttempted",
  "threePointerPct",
  "twoPointers",
  "twoPointersAttempted",
  "twoPointerPct",
  "effectiveFieldGoalPct",
  "freeThrows",
  "freeThrowsAttempted",
  "freeThrowPct",
  "offensiveRebounds",
  "defensiveRebounds",
  "totalRebounds",
  "assists",
  "steals",
  "blocks",
  "turnovers",
  "personalFouls",
  "points",
];

const addFormFields = [
  { key: "name", label: "Name", type: "text", required: true, wide: true },
  { key: "age", label: "Age", type: "number" },
  { key: "team", label: "Team", type: "text", required: true },
  { key: "position", label: "Position", type: "text", required: true },
  { key: "games", label: "Games", type: "number" },
  { key: "gamesStarted", label: "Games started", type: "number" },
  { key: "minutesPlayed", label: "Minutes", type: "number", step: "0.1" },
  { key: "points", label: "Points", type: "number", step: "0.1" },
  { key: "totalRebounds", label: "Rebounds", type: "number", step: "0.1" },
  { key: "assists", label: "Assists", type: "number", step: "0.1" },
  { key: "steals", label: "Steals", type: "number", step: "0.1" },
  { key: "blocks", label: "Blocks", type: "number", step: "0.1" },
  { key: "turnovers", label: "Turnovers", type: "number", step: "0.1" },
  { key: "fieldGoalPct", label: "FG pct", type: "number", step: "0.001" },
  { key: "threePointerPct", label: "3P pct", type: "number", step: "0.001" },
  { key: "freeThrowPct", label: "FT pct", type: "number", step: "0.001" },
  { key: "fieldGoals", label: "FG", type: "number", step: "0.1" },
  { key: "fieldGoalsAttempted", label: "FGA", type: "number", step: "0.1" },
  { key: "threePointers", label: "3P", type: "number", step: "0.1" },
  { key: "threePointersAttempted", label: "3PA", type: "number", step: "0.1" },
  { key: "twoPointers", label: "2P", type: "number", step: "0.1" },
  { key: "twoPointersAttempted", label: "2PA", type: "number", step: "0.1" },
  { key: "effectiveFieldGoalPct", label: "eFG pct", type: "number", step: "0.001" },
  { key: "freeThrows", label: "FT", type: "number", step: "0.1" },
  { key: "freeThrowsAttempted", label: "FTA", type: "number", step: "0.1" },
  { key: "offensiveRebounds", label: "Off rebounds", type: "number", step: "0.1" },
  { key: "defensiveRebounds", label: "Def rebounds", type: "number", step: "0.1" },
  { key: "personalFouls", label: "Fouls", type: "number", step: "0.1" },
];

const editFormFields = [
  { key: "name", label: "Name", type: "text", required: true, wide: true, readonly: true },
  { key: "team", label: "Team", type: "text", required: true },
  { key: "position", label: "Position", type: "text", required: true },
];

const state = {
  players: [],
  currentPrediction: null,
  topPredictions: [],
  modelSummary: null,
  predictionLoading: false,
  predictionRequestId: 0,
  selectedUid: "",
  pendingSelection: null,
  sortKey: "points",
  sortDirection: "desc",
  dialogMode: "add",
};

ensurePredictionShell();

const els = {
  apiStatus: document.querySelector("#apiStatus"),
  refreshButton: document.querySelector("#refreshButton"),
  addPlayerButton: document.querySelector("#addPlayerButton"),
  totalPlayers: document.querySelector("#totalPlayers"),
  totalTeams: document.querySelector("#totalTeams"),
  averagePoints: document.querySelector("#averagePoints"),
  impactLeader: document.querySelector("#impactLeader"),
  heroTopPoints: document.querySelector("#heroTopPoints"),
  nameFilter: document.querySelector("#nameFilter"),
  quickSearch: document.querySelector("#quickSearch"),
  teamFilter: document.querySelector("#teamFilter"),
  positionFilter: document.querySelector("#positionFilter"),
  minimumGames: document.querySelector("#minimumGames"),
  minimumGamesValue: document.querySelector("#minimumGamesValue"),
  clearFiltersButton: document.querySelector("#clearFiltersButton"),
  leadersList: document.querySelector("#leadersList"),
  visibleCount: document.querySelector("#visibleCount"),
  playersTableBody: document.querySelector("#playersTableBody"),
  playerDetailPanel: document.querySelector("#playerDetailPanel"),
  predictionPanel: document.querySelector("#predictionPanel"),
  modelStatus: document.querySelector("#modelStatus"),
  retrainModelButton: document.querySelector("#retrainModelButton"),
  topPredictionsList: document.querySelector("#topPredictionsList"),
  compareA: document.querySelector("#compareA"),
  compareB: document.querySelector("#compareB"),
  compareOutput: document.querySelector("#compareOutput"),
  playerDialog: document.querySelector("#playerDialog"),
  playerForm: document.querySelector("#playerForm"),
  formFields: document.querySelector("#formFields"),
  dialogEyebrow: document.querySelector("#dialogEyebrow"),
  dialogTitle: document.querySelector("#dialogTitle"),
  cancelDialogButton: document.querySelector("#cancelDialogButton"),
  savePlayerButton: document.querySelector("#savePlayerButton"),
  toast: document.querySelector("#toast"),
};

function ensurePredictionShell() {
  if (document.querySelector("#predictionPanel")) {
    return;
  }

  const main = document.querySelector("main");
  if (!main) {
    return;
  }

  const section = document.createElement("section");
  section.className = "prediction-grid";
  section.setAttribute("aria-label", "Next season predictions");
  section.innerHTML = `
    <article class="forecast-panel" id="predictionPanel" aria-live="polite">
      <div class="empty-state compact-empty">
        <span class="empty-mark" aria-hidden="true">↗</span>
        <h2>Select a player</h2>
        <p>Next-season projections appear here once the model is ready.</p>
      </div>
    </article>

    <article class="projection-panel">
      <div class="panel-heading">
        <div>
          <p class="eyebrow">Model board</p>
          <h2>Top projected scorers</h2>
        </div>
        <button class="ghost-button" id="retrainModelButton" type="button">Retrain</button>
      </div>
      <p class="model-status" id="modelStatus">Training model</p>
      <div id="topPredictionsList" class="top-predictions-list"></div>
    </article>`;
  main.appendChild(section);
}

async function loadPlayers() {
  setApiStatus("Connecting", "");
  els.playersTableBody.innerHTML = `<tr><td colspan="10" class="loading-row">Loading players</td></tr>`;

  try {
    const response = await fetch(API_URL);
    if (!response.ok) {
      throw new Error(`Request failed with ${response.status}`);
    }
    const previousSelection = selectedPlayer();
    state.players = withClientIds(await response.json());
    const preferredSelection = state.pendingSelection || previousSelection;
    state.pendingSelection = null;
    state.selectedUid = findMatchingPlayer(preferredSelection)?._uid || state.players[0]?._uid || "";
    setApiStatus("API ready", "ready");
    hydrateFilters();
    render();
    loadPredictionData();
  } catch (error) {
    setApiStatus("API unavailable", "error");
    els.playersTableBody.innerHTML = `<tr><td colspan="10" class="empty-row">Could not load players from ${API_URL}</td></tr>`;
    showToast(error.message || "Could not load players");
  }
}

function setApiStatus(text, className) {
  els.apiStatus.textContent = text;
  els.apiStatus.className = `api-pill ${className}`;
}

function hydrateFilters() {
  const teams = uniqueSorted(state.players.map((player) => player.team).filter(Boolean));
  const positions = uniqueSorted(state.players.map((player) => player.position).filter(Boolean));

  replaceSelectOptions(els.teamFilter, "All teams", teams);
  replaceSelectOptions(els.positionFilter, "All positions", positions);
  replacePlayerSelectOptions(els.compareA);
  replacePlayerSelectOptions(els.compareB);

  if (!els.compareA.value && state.players[0]) {
    els.compareA.value = state.players[0]._uid;
  }
  if (!els.compareB.value && state.players[1]) {
    els.compareB.value = state.players[1]._uid;
  }
}

function replaceSelectOptions(select, placeholder, values) {
  const currentValue = select.value;
  select.innerHTML = `<option value="">${placeholder}</option>${values
    .map((value) => `<option value="${escapeHtml(value)}">${escapeHtml(value)}</option>`)
    .join("")}`;
  select.value = values.includes(currentValue) ? currentValue : "";
}

function replacePlayerSelectOptions(select) {
  const currentValue = select.value;
  select.innerHTML = state.players
    .slice()
    .sort((a, b) => a.name.localeCompare(b.name))
    .map((player) => `<option value="${escapeHtml(player._uid)}">${escapeHtml(player.name)} · ${escapeHtml(player.team)} ${escapeHtml(player.position)}</option>`)
    .join("");
  select.value = state.players.some((player) => player._uid === currentValue) ? currentValue : select.options[0]?.value || "";
}

function render() {
  const players = filteredPlayers();
  const sortedPlayers = sortPlayers(players);
  renderMetrics(players);
  renderLeaders(players);
  renderTable(sortedPlayers);
  renderDetail();
  renderPrediction();
  renderModelSummary();
  renderTopPredictions();
  renderCompare();
  syncSortButtons();
}

function filteredPlayers() {
  const name = els.nameFilter.value.trim().toLowerCase();
  const quick = els.quickSearch.value.trim().toLowerCase();
  const team = els.teamFilter.value;
  const position = els.positionFilter.value;
  const minimumGames = Number(els.minimumGames.value);

  return state.players.filter((player) => {
    const searchable = `${player.name} ${player.team} ${player.position}`.toLowerCase();
    return (
      (!name || player.name.toLowerCase().includes(name)) &&
      (!quick || searchable.includes(quick)) &&
      (!team || player.team === team) &&
      (!position || player.position === position) &&
      Number(player.games || 0) >= minimumGames
    );
  });
}

function sortPlayers(players) {
  return players.slice().sort((a, b) => {
    const aValue = a[state.sortKey];
    const bValue = b[state.sortKey];
    const direction = state.sortDirection === "asc" ? 1 : -1;

    if (typeof aValue === "number" && typeof bValue === "number") {
      return (aValue - bValue) * direction;
    }

    return String(aValue || "").localeCompare(String(bValue || "")) * direction;
  });
}

function renderMetrics(players) {
  const teams = new Set(state.players.map((player) => player.team).filter(Boolean));
  const averagePoints = average(players.map((player) => player.points));
  const impactLeader = topBy(state.players, impactIndex);
  const scoringLeader = topBy(state.players, (player) => player.points);

  els.totalPlayers.textContent = formatWhole(state.players.length);
  els.totalTeams.textContent = formatWhole(teams.size);
  els.averagePoints.textContent = formatNumber(averagePoints);
  els.impactLeader.textContent = impactLeader?.name || "--";
  els.heroTopPoints.textContent = scoringLeader ? formatNumber(scoringLeader.points) : "--";
  els.visibleCount.textContent = formatWhole(players.length);
}

function renderLeaders(players) {
  const leaderConfig = [
    { label: "Scoring", key: "points", suffix: "PPG" },
    { label: "Rebounding", key: "totalRebounds", suffix: "RPG" },
    { label: "Playmaking", key: "assists", suffix: "APG" },
    { label: "Efficiency", key: "effectiveFieldGoalPct", suffix: "eFG" },
  ];

  els.leadersList.innerHTML = leaderConfig
    .map((item) => {
      const player = topBy(players, (row) => Number(row[item.key] || 0));
      if (!player) {
        return "";
      }
      const value = item.key.includes("Pct") ? formatPercent(player[item.key]) : formatNumber(player[item.key]);
      return `
        <div class="leader-card">
          <button type="button" data-select="${escapeHtml(player._uid)}">
            <strong>${escapeHtml(player.name)}</strong>
            <span>${item.label} · ${escapeHtml(player.team)} ${escapeHtml(player.position)}</span>
          </button>
          <div class="leader-value">${value} ${item.suffix}</div>
        </div>`;
    })
    .join("");
}

function renderTable(players) {
  if (!players.length) {
    els.playersTableBody.innerHTML = `<tr><td colspan="10" class="empty-row">No matching players</td></tr>`;
    return;
  }

  els.playersTableBody.innerHTML = players
    .map((player) => {
      const selected = player._uid === state.selectedUid ? "selected" : "";
      return `
        <tr class="${selected}" data-select="${escapeHtml(player._uid)}">
          <td>
            <div class="player-cell">
              <span class="avatar">${initials(player.name)}</span>
              <span>
                <strong>${escapeHtml(player.name)}</strong>
                <small>Age ${formatWhole(player.age)} · ${formatNumber(impactIndex(player))} index</small>
              </span>
            </div>
          </td>
          <td><span class="team-badge">${escapeHtml(player.team)}</span></td>
          <td><span class="position-badge">${escapeHtml(player.position)}</span></td>
          <td>${formatNumber(player.points)}</td>
          <td>${formatNumber(player.totalRebounds)}</td>
          <td>${formatNumber(player.assists)}</td>
          <td>${formatPercent(player.fieldGoalPct)}</td>
          <td>${formatPercent(player.threePointerPct)}</td>
          <td>${formatWhole(player.games)}</td>
          <td class="actions-column">
            <div class="actions-cell">
              <button class="mini-button" type="button" data-edit="${escapeHtml(player._uid)}">Edit</button>
              <button class="mini-button delete" type="button" data-delete="${escapeHtml(player._uid)}">Delete</button>
            </div>
          </td>
        </tr>`;
    })
    .join("");
}

function renderDetail() {
  const player = selectedPlayer();
  if (!player) {
    els.playerDetailPanel.innerHTML = `
      <div class="empty-state">
        <span class="empty-mark" aria-hidden="true">⌁</span>
        <h2>Select a player</h2>
        <p>Player profile, scoring mix, and impact index appear here.</p>
      </div>`;
    return;
  }

  const statTiles = [
    ["PTS", player.points],
    ["REB", player.totalRebounds],
    ["AST", player.assists],
    ["STL", player.steals],
    ["BLK", player.blocks],
    ["FG%", formatPercent(player.fieldGoalPct)],
    ["3P%", formatPercent(player.threePointerPct)],
    ["MIN", player.minutesPlayed],
  ];

  const barRows = [
    ["Usage scoring", player.points, 35],
    ["Playmaking", player.assists, 12],
    ["Glass control", player.totalRebounds, 15],
    ["Defensive events", Number(player.steals || 0) + Number(player.blocks || 0), 6],
    ["Shooting efficiency", Number(player.effectiveFieldGoalPct || 0) * 100, 70],
  ];

  els.playerDetailPanel.innerHTML = `
    <div class="detail-header">
      <div>
        <p class="eyebrow">Player profile</p>
        <h2>${escapeHtml(player.name)}</h2>
        <div class="detail-meta">
          <span class="team-badge">${escapeHtml(player.team)}</span>
          <span class="position-badge">${escapeHtml(player.position)}</span>
          <span class="team-badge">${formatWhole(player.games)} games</span>
        </div>
      </div>
      <div class="impact-score" aria-label="Impact index ${formatNumber(impactIndex(player))}">
        <span>${formatNumber(impactIndex(player))}</span>
      </div>
    </div>
    <div class="stat-grid">
      ${statTiles
        .map(
          ([label, value]) => `
          <div class="stat-tile">
            <span>${label}</span>
            <strong>${typeof value === "string" ? value : formatNumber(value)}</strong>
          </div>`
        )
        .join("")}
    </div>
    <div class="bars">
      ${barRows
        .map(([label, value, max]) => {
          const pct = clamp((Number(value || 0) / max) * 100, 0, 100);
          return `
            <div class="bar-row">
              <div class="bar-label"><span>${label}</span><strong>${formatNumber(value)}</strong></div>
              <div class="bar-track"><div class="bar-fill" style="width: ${pct}%"></div></div>
            </div>`;
        })
        .join("")}
    </div>`;
}

function renderCompare() {
  const first = state.players.find((player) => player._uid === els.compareA.value);
  const second = state.players.find((player) => player._uid === els.compareB.value);

  if (!first || !second) {
    els.compareOutput.innerHTML = `<div class="empty-state"><p>No comparison available</p></div>`;
    return;
  }

  const rows = [
    ["PTS", first.points, second.points],
    ["REB", first.totalRebounds, second.totalRebounds],
    ["AST", first.assists, second.assists],
    ["STL", first.steals, second.steals],
    ["BLK", first.blocks, second.blocks],
    ["eFG%", first.effectiveFieldGoalPct, second.effectiveFieldGoalPct, true],
    ["Index", impactIndex(first), impactIndex(second)],
  ];

  els.compareOutput.innerHTML = rows
    .map(([label, a, b, percent]) => {
      const total = Number(a || 0) + Number(b || 0);
      const left = total > 0 ? clamp((Number(a || 0) / total) * 100, 4, 96) : 50;
      const aText = percent ? formatPercent(a) : formatNumber(a);
      const bText = percent ? formatPercent(b) : formatNumber(b);
      return `
        <div class="compare-row">
          <strong>${label}</strong>
          <div class="compare-bar" style="--left: ${left}%"><span></span><span></span></div>
          <div class="compare-values">${aText} / ${bText}</div>
        </div>`;
    })
    .join("");
}

function renderPrediction() {
  const player = selectedPlayer();
  if (!player) {
    els.predictionPanel.innerHTML = `
      <div class="empty-state compact-empty">
        <span class="empty-mark" aria-hidden="true">↗</span>
        <h2>Select a player</h2>
        <p>Next-season projections appear here once the model is ready.</p>
      </div>`;
    return;
  }

  const prediction = state.currentPrediction;
  const predictionMatches =
    prediction &&
    prediction.name === player.name &&
    prediction.team === player.team &&
    prediction.position === player.position;

  if (state.predictionLoading || !predictionMatches) {
    els.predictionPanel.innerHTML = `
      <div class="empty-state compact-empty">
        <span class="empty-mark" aria-hidden="true">↗</span>
        <h2>Training forecast</h2>
        <p>Building a next-season projection for ${escapeHtml(player.name)}.</p>
      </div>`;
    return;
  }

  const stats = [
    ["PTS", prediction.current.points, prediction.predicted.points, false],
    ["REB", prediction.current.totalRebounds, prediction.predicted.totalRebounds, false],
    ["AST", prediction.current.assists, prediction.predicted.assists, false],
    ["MIN", prediction.current.minutesPlayed, prediction.predicted.minutesPlayed, false],
    ["eFG%", prediction.current.effectiveFieldGoalPct, prediction.predicted.effectiveFieldGoalPct, true],
    ["3P%", prediction.current.threePointerPct, prediction.predicted.threePointerPct, true],
  ];

  els.predictionPanel.innerHTML = `
    <div class="forecast-header">
      <div>
        <p class="eyebrow">Next season forecast</p>
        <h2>${escapeHtml(prediction.name)}</h2>
        <div class="detail-meta">
          <span class="team-badge">Age ${formatWhole(prediction.currentAge)} → ${formatWhole(prediction.predictedAge)}</span>
          <span class="position-badge">${escapeHtml(prediction.position)}</span>
          <span class="team-badge">${formatPercent(prediction.confidence)} confidence</span>
        </div>
      </div>
      <div class="forecast-score">
        <strong>${formatNumber(prediction.predicted.points)}</strong>
        <span>projected PPG</span>
      </div>
    </div>
    <div class="forecast-stat-grid">
      ${stats
        .map(([label, current, projected, percent]) => {
          const delta = projected - current;
          const currentText = percent ? formatPercent(current) : formatNumber(current);
          const projectedText = percent ? formatPercent(projected) : formatNumber(projected);
          const deltaText = percent ? formatSignedPercent(delta) : formatSigned(delta);
          return `
            <div class="forecast-stat">
              <span>${label}</span>
              <strong>${projectedText}</strong>
              <small class="${deltaClass(delta)}">${deltaText} from ${currentText}</small>
            </div>`;
        })
        .join("")}
    </div>
    <div class="model-note">
      <strong>Model inputs</strong>
      <div class="input-chip-row">
        ${prediction.modelInputs.map((input) => `<span>${escapeHtml(input)}</span>`).join("")}
      </div>
      <p>${escapeHtml(prediction.modelNote)}</p>
    </div>`;
}

function renderModelSummary() {
  if (!state.modelSummary) {
    els.modelStatus.textContent = "Training model";
    return;
  }

  const trainedAt = new Date(state.modelSummary.trainedAt).toLocaleTimeString([], {
    hour: "numeric",
    minute: "2-digit",
  });
  els.modelStatus.textContent = `${state.modelSummary.modelName} · ${state.modelSummary.trainingSamples} samples · ${state.modelSummary.targetStats.length} targets · trained ${trainedAt}`;
}

function renderTopPredictions() {
  if (!state.topPredictions.length) {
    els.topPredictionsList.innerHTML = `
      <div class="empty-state top-empty">
        <p>No projections loaded yet</p>
      </div>`;
    return;
  }

  els.topPredictionsList.innerHTML = state.topPredictions
    .map((prediction, index) => {
      const player = findMatchingPlayer(prediction);
      const selectAttribute = player ? `data-select="${escapeHtml(player._uid)}"` : "";
      return `
        <button class="projection-card" type="button" ${selectAttribute}>
          <span class="projection-rank">${index + 1}</span>
          <span>
            <strong>${escapeHtml(prediction.name)}</strong>
            <small>${escapeHtml(prediction.team)} ${escapeHtml(prediction.position)} · ${formatPercent(prediction.confidence)} confidence</small>
          </span>
          <span class="projection-value">
            ${formatNumber(prediction.predicted.points)}
            <small>${formatSigned(prediction.delta.points)}</small>
          </span>
        </button>`;
    })
    .join("");
}

function syncSortButtons() {
  document.querySelectorAll("[data-sort]").forEach((button) => {
    const isActive = button.dataset.sort === state.sortKey;
    button.classList.toggle("active", isActive);
    button.textContent = button.textContent.replace(/\s[↑↓]$/, "");
    if (isActive) {
      button.textContent += state.sortDirection === "asc" ? " ↑" : " ↓";
    }
  });
}

function selectedPlayer() {
  return state.players.find((player) => player._uid === state.selectedUid);
}

async function loadPredictionData() {
  await Promise.all([loadModelSummary(), loadTopPredictions(), loadSelectedPrediction()]);
}

async function loadModelSummary() {
  try {
    const response = await fetch(`${PREDICTION_URL}/model`);
    if (!response.ok) {
      throw new Error(`Model summary failed with ${response.status}`);
    }
    state.modelSummary = await response.json();
    renderModelSummary();
  } catch (error) {
    els.modelStatus.textContent = "Prediction model unavailable";
  }
}

async function loadTopPredictions() {
  try {
    const response = await fetch(`${PREDICTION_URL}?limit=8`);
    if (!response.ok) {
      throw new Error(`Top predictions failed with ${response.status}`);
    }
    state.topPredictions = await response.json();
    renderTopPredictions();
  } catch (error) {
    state.topPredictions = [];
    renderTopPredictions();
  }
}

async function loadSelectedPrediction() {
  const player = selectedPlayer();
  const requestId = state.predictionRequestId + 1;
  state.predictionRequestId = requestId;
  state.currentPrediction = null;

  if (!player) {
    state.predictionLoading = false;
    renderPrediction();
    return;
  }

  state.predictionLoading = true;
  renderPrediction();

  try {
    const query = new URLSearchParams({
      name: player.name,
      team: player.team || "",
      pos: player.position || "",
    });
    const response = await fetch(`${PREDICTION_URL}?${query.toString()}`);
    if (!response.ok) {
      throw new Error(`Prediction failed with ${response.status}`);
    }
    const prediction = await response.json();
    if (requestId === state.predictionRequestId) {
      state.currentPrediction = prediction;
    }
  } catch (error) {
    showToast(error.message || "Could not load prediction");
  } finally {
    if (requestId === state.predictionRequestId) {
      state.predictionLoading = false;
      renderPrediction();
    }
  }
}

async function retrainModel() {
  els.retrainModelButton.disabled = true;
  els.retrainModelButton.textContent = "Training";

  try {
    const response = await fetch(`${PREDICTION_URL}/train`, { method: "POST" });
    if (!response.ok) {
      throw new Error(`Training failed with ${response.status}`);
    }
    state.modelSummary = await response.json();
    await Promise.all([loadTopPredictions(), loadSelectedPrediction()]);
    renderModelSummary();
    showToast("Prediction model retrained");
  } catch (error) {
    showToast(error.message || "Could not retrain model");
  } finally {
    els.retrainModelButton.disabled = false;
    els.retrainModelButton.textContent = "Retrain";
  }
}

function selectPlayer(uid) {
  if (!uid || state.selectedUid === uid) {
    return;
  }

  state.selectedUid = uid;
  state.currentPrediction = null;
  render();
  loadSelectedPrediction();
}

function openDialog(mode, player = {}) {
  state.dialogMode = mode;
  const fields = mode === "edit" ? editFormFields : addFormFields;

  els.dialogEyebrow.textContent = mode === "edit" ? "Roster identity" : "Player record";
  els.dialogTitle.textContent = mode === "edit" ? `Edit ${player.name}` : "Add player";
  els.savePlayerButton.textContent = mode === "edit" ? "Update player" : "Save player";
  els.formFields.innerHTML = fields.map((field) => renderFormField(field, player)).join("");
  els.playerDialog.showModal();
}

function renderFormField(field, player) {
  const value = player[field.key] ?? "";
  const readonly = field.readonly ? "readonly" : "";
  const required = field.required ? "required" : "";
  const step = field.step ? `step="${field.step}"` : field.type === "number" ? `step="1"` : "";
  return `
    <label class="form-field ${field.wide ? "wide" : ""}">
      <span class="field-label">${field.label}</span>
      <input
        name="${field.key}"
        type="${field.type}"
        value="${escapeHtml(String(value))}"
        ${step}
        ${required}
        ${readonly}
      />
    </label>`;
}

async function savePlayer(event) {
  event.preventDefault();
  const formData = new FormData(els.playerForm);
  const payload = {};
  const fields = state.dialogMode === "edit" ? editFormFields : addFormFields;

  for (const field of fields) {
    const value = formData.get(field.key);
    payload[field.key] = numericFields.includes(field.key) ? Number(value || 0) : String(value || "").trim();
  }

  if (!payload.name) {
    showToast("Player name is required");
    return;
  }

  try {
    const response = await fetch(API_URL, {
      method: state.dialogMode === "edit" ? "PUT" : "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });

    if (!response.ok) {
      throw new Error(`Save failed with ${response.status}`);
    }

    els.playerDialog.close();
    state.pendingSelection = payload;
    await loadPlayers();
    showToast(state.dialogMode === "edit" ? "Player updated" : "Player added");
  } catch (error) {
    showToast(error.message || "Could not save player");
  }
}

async function deletePlayer(uid) {
  const player = state.players.find((item) => item._uid === uid);
  if (!player) {
    return;
  }

  const confirmed = window.confirm(`Delete ${player.name}?`);
  if (!confirmed) {
    return;
  }

  try {
    const response = await fetch(`${API_URL}/${encodeURIComponent(player.name)}`, { method: "DELETE" });
    if (!response.ok) {
      throw new Error(`Delete failed with ${response.status}`);
    }

    if (state.selectedUid === uid) {
      state.selectedUid = "";
    }
    await loadPlayers();
    showToast("Player deleted");
  } catch (error) {
    showToast(error.message || "Could not delete player");
  }
}

function showToast(message) {
  els.toast.textContent = message;
  els.toast.classList.add("show");
  window.clearTimeout(showToast.timeout);
  showToast.timeout = window.setTimeout(() => els.toast.classList.remove("show"), 2800);
}

function impactIndex(player) {
  const scoring = Number(player.points || 0);
  const creation = Number(player.assists || 0) * 1.35;
  const rebounding = Number(player.totalRebounds || 0) * 0.9;
  const defense = (Number(player.steals || 0) + Number(player.blocks || 0)) * 2.4;
  const efficiency = Number(player.effectiveFieldGoalPct || player.fieldGoalPct || 0) * 18;
  const availability = Math.min(Number(player.games || 0) / 82, 1) * 4;
  const turnovers = Number(player.turnovers || 0) * 0.9;
  return scoring + creation + rebounding + defense + efficiency + availability - turnovers;
}

function withClientIds(players) {
  return players.map((player, index) => ({
    ...player,
    _uid: `player-${index}`,
  }));
}

function findMatchingPlayer(reference) {
  if (!reference) {
    return null;
  }

  return (
    state.players.find((player) => player._uid === reference._uid) ||
    state.players.find(
      (player) =>
        player.name === reference.name &&
        player.team === reference.team &&
        player.position === reference.position
    ) ||
    state.players.find((player) => player.name === reference.name) ||
    null
  );
}

function topBy(players, accessor) {
  return players.reduce((best, player) => {
    if (!best) {
      return player;
    }
    return Number(accessor(player) || 0) > Number(accessor(best) || 0) ? player : best;
  }, null);
}

function average(values) {
  const numeric = values.map(Number).filter((value) => Number.isFinite(value));
  return numeric.length ? numeric.reduce((sum, value) => sum + value, 0) / numeric.length : 0;
}

function formatNumber(value) {
  const number = Number(value || 0);
  return number.toLocaleString(undefined, { maximumFractionDigits: 1 });
}

function formatWhole(value) {
  return Number(value || 0).toLocaleString(undefined, { maximumFractionDigits: 0 });
}

function formatPercent(value) {
  const number = Number(value || 0);
  return `${(number <= 1 ? number * 100 : number).toLocaleString(undefined, { maximumFractionDigits: 1 })}%`;
}

function formatSigned(value) {
  const number = Number(value || 0);
  const prefix = number > 0 ? "+" : "";
  return `${prefix}${formatNumber(number)}`;
}

function formatSignedPercent(value) {
  const number = Number(value || 0);
  const prefix = number > 0 ? "+" : "";
  return `${prefix}${formatPercent(number)}`;
}

function deltaClass(value) {
  const number = Number(value || 0);
  if (number > 0.05) {
    return "delta-positive";
  }
  if (number < -0.05) {
    return "delta-negative";
  }
  return "delta-neutral";
}

function initials(name) {
  return String(name || "")
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0])
    .join("")
    .toUpperCase();
}

function uniqueSorted(values) {
  return [...new Set(values)].sort((a, b) => a.localeCompare(b));
}

function clamp(value, min, max) {
  return Math.min(Math.max(value, min), max);
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

els.refreshButton.addEventListener("click", loadPlayers);
els.addPlayerButton.addEventListener("click", () => openDialog("add"));
els.retrainModelButton.addEventListener("click", retrainModel);
els.cancelDialogButton.addEventListener("click", () => els.playerDialog.close());
els.playerForm.addEventListener("submit", savePlayer);

[els.nameFilter, els.quickSearch, els.teamFilter, els.positionFilter, els.minimumGames].forEach((input) => {
  input.addEventListener("input", () => {
    els.minimumGamesValue.textContent = els.minimumGames.value;
    render();
  });
});

els.clearFiltersButton.addEventListener("click", () => {
  els.nameFilter.value = "";
  els.quickSearch.value = "";
  els.teamFilter.value = "";
  els.positionFilter.value = "";
  els.minimumGames.value = "0";
  els.minimumGamesValue.textContent = "0";
  render();
});

document.addEventListener("click", (event) => {
  const sortButton = event.target.closest("[data-sort]");
  const selectButton = event.target.closest("[data-select]");
  const editButton = event.target.closest("[data-edit]");
  const deleteButton = event.target.closest("[data-delete]");

  if (sortButton) {
    const sortKey = sortButton.dataset.sort;
    if (state.sortKey === sortKey) {
      state.sortDirection = state.sortDirection === "asc" ? "desc" : "asc";
    } else {
      state.sortKey = sortKey;
      state.sortDirection = numericFields.includes(sortKey) ? "desc" : "asc";
    }
    render();
  }

  if (selectButton && !editButton && !deleteButton) {
    selectPlayer(selectButton.dataset.select);
  }

  if (editButton) {
    const player = state.players.find((item) => item._uid === editButton.dataset.edit);
    if (player) {
      openDialog("edit", player);
    }
  }

  if (deleteButton) {
    deletePlayer(deleteButton.dataset.delete);
  }
});

els.playersTableBody.addEventListener("click", (event) => {
  if (event.target.closest("button")) {
    return;
  }

  const row = event.target.closest("[data-select]");
  if (row) {
    selectPlayer(row.dataset.select);
  }
});

[els.compareA, els.compareB].forEach((select) => select.addEventListener("input", renderCompare));

loadPlayers();
