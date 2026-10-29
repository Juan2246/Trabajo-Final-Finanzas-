"use strict";

// Las alas responden al estado real del campo, incluso con el autocompletado.
function mascotIllustration() {
  return `<div class="login-mascot" data-mascot aria-hidden="true">
    <svg viewBox="0 0 400 330" class="owl-scene" fill="none">
      <ellipse cx="200" cy="302" rx="128" ry="12" fill="#294D37"/>
      <circle cx="200" cy="162" r="136" fill="#D9E8BD"/>
      <path d="M71 243c-21-12-30-35-23-52 19 10 30 26 23 52Z" fill="#92B081"/>
      <path d="M327 252c21-17 25-38 16-53-18 13-24 29-16 53Z" fill="#92B081"/>
      <path d="M88 276c-8-18-20-27-36-24 4 19 17 29 36 24Z" fill="#B6CB9C"/>
      <path d="M308 278c10-18 25-25 38-18-7 18-20 25-38 18Z" fill="#B6CB9C"/>
      <path d="M132 277c-9 7-14 16-10 23 6 8 19 4 25-1 5 8 19 7 23 1 4-7-1-17-9-22" fill="#B27B4E"/>
      <path d="M240 277c-9 7-14 16-10 23 6 8 19 4 25-1 5 8 19 7 23 1 4-7-1-17-9-22" fill="#B27B4E"/>
      <path d="M110 156c-14 36-20 89 1 114 28 33 148 33 177 0 23-27 13-80-1-114" fill="#C99869"/>
      <path d="M141 168c-18 27-23 65-6 88 23 29 108 29 131 0 15-21 12-60-8-88" fill="#F5E5CE"/>
      <path d="M111 126c-14-23-16-66-1-91 21 5 39 17 50 30 25-10 55-10 80 0 12-13 31-25 50-30 14 27 12 69-1 91 12 21 10 44-7 60-35 34-128 35-164 2-20-18-23-40-7-62Z" fill="#DDB58A"/>
      <path d="m114 52 32 24-26 17Z" fill="#B88456"/>
      <path d="m286 52-32 24 26 17Z" fill="#B88456"/>
      <path d="M200 102c-22-28-62-29-84-7-32 33-10 91 31 96 23 3 41-6 53-19 11 13 30 22 53 19 41-5 63-63 31-96-22-22-62-21-84 7Z" fill="#FFF9EF"/>
      <path d="M193 76q7 10 14 0M176 80q4 11 10 8M214 88q6 3 10-8" stroke="#B88456" stroke-width="3.5" stroke-linecap="round"/>
      <g class="owl-eyes"><circle cx="153" cy="133" r="25" fill="#EBDDCB"/><circle cx="247" cy="133" r="25" fill="#EBDDCB"/>
        <g class="owl-pupils"><ellipse cx="155" cy="134" rx="13" ry="16" fill="#253D29"/><ellipse cx="245" cy="134" rx="13" ry="16" fill="#253D29"/><circle cx="159" cy="128" r="4" fill="white"/><circle cx="249" cy="128" r="4" fill="white"/></g>
      </g>
      <path d="M187 153q13-10 26 0l-10 15q-3 4-6 0Z" fill="#D99A4B"/>
      <ellipse cx="128" cy="164" rx="12" ry="6" fill="#EAC0A6"/>
      <ellipse cx="272" cy="164" rx="12" ry="6" fill="#EAC0A6"/>
      <path d="M155 219q6 8 12 0M192 225q8 9 16 0M233 219q6 8 12 0M173 246q6 8 12 0M215 246q6 8 12 0" stroke="#D9C4A7" stroke-width="3" stroke-linecap="round"/>
      <g class="owl-wing owl-wing-left"><path d="M106 204c-16 8-25 43-17 62 7 16 31 18 47 5 18-15 19-45 8-59-9-12-25-14-38-8Z" fill="#C18C5B"/><path d="M102 241q7 12 18 10M113 227q7 12 18 10M99 255q7 12 18 10" stroke="#A77349" stroke-width="3" stroke-linecap="round"/></g>
      <g class="owl-wing owl-wing-right"><path d="M294 204c16 8 25 43 17 62-7 16-31 18-47 5-18-15-19-45-8-59 9-12 25-14 38-8Z" fill="#C18C5B"/><path d="M298 241q-7 12-18 10M287 227q-7 12-18 10M301 255q-7 12-18 10" stroke="#A77349" stroke-width="3" stroke-linecap="round"/></g>
      <path d="M72 281h256" stroke="#83A16C" stroke-width="4" stroke-linecap="round"/>
    </svg>
  </div>`;
}

function updateMascot() {
  const mascot = document.querySelector("[data-mascot]");
  const password = document.querySelector("#login-password");
  if (!mascot || !password) return;
  const covered = password.type === "text";
  mascot.classList.toggle("eyes-covered", covered);
  mascot.dataset.eyes = covered ? "covered" : "open";
  const message = document.querySelector("#mascot-message");
  if (message)
    message.textContent = covered
      ? "Tú revisa tu contraseña. Yo no miro."
      : "Todo listo para poner tus cuentas al día.";
}

let mascotFrame = 0;
document.addEventListener(
  "pointermove",
  (event) => {
    const mascot = document.querySelector("[data-mascot]");
    if (
      !mascot ||
      mascot.classList.contains("eyes-covered") ||
      matchMedia("(prefers-reduced-motion: reduce)").matches
    )
      return;
    cancelAnimationFrame(mascotFrame);
    const x = Math.max(
      -5,
      Math.min(5, (event.clientX / innerWidth - 0.5) * 10),
    );
    const y = Math.max(
      -3,
      Math.min(3, (event.clientY / innerHeight - 0.5) * 6),
    );
    mascotFrame = requestAnimationFrame(() => {
      mascot.style.setProperty("--gaze-x", x + "px");
      mascot.style.setProperty("--gaze-y", y + "px");
    });
  },
  { passive: true },
);
document.addEventListener("input", (event) => {
  if (event.target.closest("#login-form")) updateMascot();
});
