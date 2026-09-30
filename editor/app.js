const $ = (id) => document.getElementById(id);
const STORAGE_KEY = "phonepad-studio-layouts-v1";
const SNAP = 5;
const MIN_W = 24, MIN_H = 24;

const templates = {
  blank: [],
  gamepad: [
    button("Left stick", 70, 235, 150, 90), button("Right stick", 860, 235, 150, 90),
    button("A", 850, 125, 82, 54), button("B", 950, 90, 82, 54), button("X", 750, 90, 82, 54), button("Y", 850, 55, 82, 54),
    button("D-pad", 245, 285, 130, 54), button("Menu", 505, 175, 88, 44), button("View", 400, 175, 88, 44),
    button("LT", 70, 70, 190, 42), button("LB", 280, 70, 110, 42), button("RB", 710, 70, 110, 42), button("RT", 840, 70, 190, 42)
  ],
  touch: [button("Move", 80, 110, 180, 64), button("Jump", 300, 110, 140, 64), button("Action", 760, 110, 180, 64), button("Menu", 475, 280, 150, 56), button("Camera", 780, 310, 180, 64)]
};

function id() { return crypto.randomUUID ? crypto.randomUUID() : `b-${Date.now()}-${Math.random()}`; }
function button(label, x, y, width = 120, height = 48) { return { id:id(), type:"button", label, x, y, width, height, reverse:false, disabled:false, glyph:"" }; }
function clone(value) { return JSON.parse(JSON.stringify(value)); }
function normalizeLayout(layout) {
  const elements = Array.isArray(layout?.elements) ? layout.elements : [];
  return { version:1, canvas:{ width:1100, height:620 }, elements:elements.map((item) => ({ ...button("Button",0,0), ...item, id:item.id || id() })) };
}

const state = { layout: normalizeLayout({elements:templates.gamepad}), selected:null, preview:false, guides:[], interaction:null, saved:{} };

function loadSaved() { try { state.saved = JSON.parse(localStorage.getItem(STORAGE_KEY) || "{}"); } catch { state.saved = {}; } renderLayoutOptions(); }
function persistSaved() { localStorage.setItem(STORAGE_KEY, JSON.stringify(state.saved)); }
function showToast(message) { const el=$("toast"); el.textContent=message; el.classList.add("show"); clearTimeout(showToast.timer); showToast.timer=setTimeout(()=>el.classList.remove("show"),1800); }
function activeElement() { return state.layout.elements.find((item)=>item.id===state.selected) || null; }
function canvasPoint(event) { const rect=$("canvas").getBoundingClientRect(); return {x:event.clientX-rect.left,y:event.clientY-rect.top}; }
function clampRect(rect) { const c=state.layout.canvas; rect.width=Math.max(MIN_W,Math.min(c.width,rect.width)); rect.height=Math.max(MIN_H,Math.min(c.height,rect.height)); rect.x=Math.max(0,Math.min(c.width-rect.width,rect.x)); rect.y=Math.max(0,Math.min(c.height-rect.height,rect.y)); return rect; }

function render() {
  const canvas=$("canvas"); canvas.querySelectorAll(".canvas-button").forEach((el)=>el.remove());
  canvas.style.width=`${state.layout.canvas.width}px`; canvas.style.height=`${state.layout.canvas.height}px`;
  state.layout.elements.forEach((item)=>{
    const el=document.createElement("div"); el.className="canvas-button"+(item.id===state.selected?" selected":"")+(item.reverse?" reverse":"")+(state.preview&&item.disabled?" disabled-preview":"")+(state.preview?" preview-enabled":"");
    el.dataset.id=item.id; el.style.left=`${item.x}px`; el.style.top=`${item.y}px`; el.style.width=`${item.width}px`; el.style.height=`${item.height}px`;
    el.innerHTML=`${item.glyph?`<span class="button-glyph">${escapeHtml(item.glyph)}</span>`:""}<span class="button-label">${escapeHtml(item.label)}</span>`;
    if(state.preview) el.addEventListener("pointerdown",()=>{if(!item.disabled)el.classList.add("active-preview")});
    if(state.preview) el.addEventListener("pointerup",()=>el.classList.remove("active-preview"));
    if(!state.preview) { el.addEventListener("pointerdown",beginMove); el.addEventListener("click",()=>select(item.id)); addHandles(el,item); }
    canvas.appendChild(el);
  });
  $("selectionSummary").textContent=state.preview?"Preview mode":(activeElement()?.label || "Nothing selected");
  $("previewToggle").textContent=state.preview?"Edit layout":"Preview"; $("canvas").classList.toggle("preview",state.preview);
  renderInspector(); renderGuides();
}
function escapeHtml(value) { return String(value).replace(/[&<>"']/g,(ch)=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;", "'":"&#039;"}[ch])); }
function addHandles(el,item) { ["nw","n","ne","e","se","s","sw","w"].forEach((direction)=>{const h=document.createElement("span");h.className=`handle ${direction}`;h.dataset.handle=direction;h.addEventListener("pointerdown",(event)=>beginResize(event,direction,item));el.appendChild(h);}); }
function select(itemId) { state.selected=itemId; render(); }

function beginMove(event) {
  if(event.target.classList.contains("handle")) return; event.preventDefault(); const item=state.layout.elements.find((v)=>v.id===event.currentTarget.dataset.id); if(!item)return;
  state.selected=item.id; render(); const target=document.querySelector(`[data-id="${item.id}"]`); if(!target)return;
  const point=canvasPoint(event); state.interaction={kind:"move",itemId:item.id,startPoint:point,startRect:{x:item.x,y:item.y,width:item.width,height:item.height},alt:event.altKey}; target.setPointerCapture(event.pointerId); target.addEventListener("pointermove",updateInteraction); target.addEventListener("pointerup",endInteraction,{once:true}); target.addEventListener("pointercancel",endInteraction,{once:true});
}
function beginResize(event,direction,item) { event.preventDefault();event.stopPropagation();state.selected=item.id;render();const handle=document.querySelector(`[data-id="${item.id}"] .handle.${direction}`);if(!handle)return;const point=canvasPoint(event);state.interaction={kind:"resize",direction,itemId:item.id,startPoint:point,startRect:{x:item.x,y:item.y,width:item.width,height:item.height},alt:event.altKey};handle.setPointerCapture(event.pointerId);handle.addEventListener("pointermove",updateInteraction);handle.addEventListener("pointerup",endInteraction,{once:true});handle.addEventListener("pointercancel",endInteraction,{once:true}); }
function updateInteraction(event) {
  const interaction=state.interaction;if(!interaction)return;const item=state.layout.elements.find((v)=>v.id===interaction.itemId);const point=canvasPoint(event);const dx=point.x-interaction.startPoint.x,dy=point.y-interaction.startPoint.y;let rect={...interaction.startRect};
  if(interaction.kind==="move"){rect.x+=dx;rect.y+=dy;} else {const d=interaction.direction;if(d.includes("e"))rect.width=interaction.startRect.width+dx;if(d.includes("s"))rect.height=interaction.startRect.height+dy;if(d.includes("w")){rect.x+=dx;rect.width-=dx;}if(d.includes("n")){rect.y+=dy;rect.height-=dy;}}
  clampRect(rect); const snap=(interaction.alt||event.altKey)?{rect,guides:[]}:snapRect(rect,item.id,interaction.kind==="resize"?interaction.direction:null); item.x=snap.rect.x;item.y=snap.rect.y;item.width=snap.rect.width;item.height=snap.rect.height;state.guides=snap.guides;
  const el=document.querySelector(`[data-id="${item.id}"]`); if(el){el.style.left=`${item.x}px`;el.style.top=`${item.y}px`;el.style.width=`${item.width}px`;el.style.height=`${item.height}px`;}
  $("selectionSummary").textContent=item.label; renderGuides(); renderInspector();
}
function endInteraction() { state.interaction=null;state.guides=[];renderGuides();renderInspector(); }

function snapRect(rect,ignoreId,direction) {
  const targets=[];const c=state.layout.canvas;targets.push({axis:"x",value:0},{axis:"x",value:c.width},{axis:"x",value:c.width/2},{axis:"y",value:0},{axis:"y",value:c.height},{axis:"y",value:c.height/2});
  state.layout.elements.filter((item)=>item.id!==ignoreId).forEach((item)=>{targets.push({axis:"x",value:item.x},{axis:"x",value:item.x+item.width},{axis:"x",value:item.x+item.width/2},{axis:"y",value:item.y},{axis:"y",value:item.y+item.height},{axis:"y",value:item.y+item.height/2});});
  const guides=[];let bestX=null,bestY=null;const xCandidates=[{value:rect.x,offset:0},{value:rect.x+rect.width,offset:rect.width},{value:rect.x+rect.width/2,offset:rect.width/2}];const yCandidates=[{value:rect.y,offset:0},{value:rect.y+rect.height,offset:rect.height},{value:rect.y+rect.height/2,offset:rect.height/2}];
  for(const candidate of xCandidates)for(const target of targets)if(target.axis==="x"&&Math.abs(candidate.value-target.value)<=SNAP&&(!bestX||Math.abs(candidate.value-target.value)<bestX.distance)){bestX={distance:Math.abs(candidate.value-target.value),value:target.value,offset:candidate.offset};}
  for(const candidate of yCandidates)for(const target of targets)if(target.axis==="y"&&Math.abs(candidate.value-target.value)<=SNAP&&(!bestY||Math.abs(candidate.value-target.value)<bestY.distance)){bestY={distance:Math.abs(candidate.value-target.value),value:target.value,offset:candidate.offset};}
  const result={...rect};if(bestX){result.x=bestX.value-bestX.offset;guides.push({axis:"x",value:bestX.value});}if(bestY){result.y=bestY.value-bestY.offset;guides.push({axis:"y",value:bestY.value});}clampRect(result);return {rect:result,guides};
}
function renderGuides(){const layer=$("guides");layer.innerHTML="";state.guides.forEach((guide)=>{const line=document.createElement("span");line.className=`guide ${guide.axis==="x"?"v":"h"}`;line.style[guide.axis==="x"?"left":"top"]=`${guide.value}px`;layer.appendChild(line);});}

function renderInspector(){const item=activeElement();$("emptyInspector").hidden=!!item;$("buttonInspector").hidden=!item;if(!item)return;$("buttonLabel").value=item.label;$("posX").value=Math.round(item.x);$("posY").value=Math.round(item.y);$("sizeW").value=Math.round(item.width);$("sizeH").value=Math.round(item.height);$("reverseButton").checked=!!item.reverse;$("disabledButton").checked=!!item.disabled;}
function bindInspector(){
  $("buttonLabel").addEventListener("input",(event)=>{const item=activeElement();if(item){item.label=event.target.value;const el=document.querySelector(`[data-id="${item.id}"] .button-label`);if(el)el.textContent=item.label;$("selectionSummary").textContent=item.label;}});
  [["posX","x"],["posY","y"],["sizeW","width"],["sizeH","height"]].forEach(([id,key])=>$(id).addEventListener("change",(event)=>{const item=activeElement();if(!item)return;item[key]=Number(event.target.value)||0;clampRect(item);render();}));
  $("reverseButton").addEventListener("change",(event)=>{const item=activeElement();if(item){item.reverse=event.target.checked;render();}});
  $("disabledButton").addEventListener("change",(event)=>{const item=activeElement();if(item){item.disabled=event.target.checked;render();}});
  $("deleteButton").addEventListener("click",()=>{if(state.selected){state.layout.elements=state.layout.elements.filter((v)=>v.id!==state.selected);state.selected=null;render();}});
}
function saveNamed(){const name=$("layoutName").value.trim();if(!name){showToast("Enter a layout name");return;}state.saved[name]=clone(state.layout);persistSaved();if(window.PhonePad&&PhonePad.saveLayout)PhonePad.saveLayout(JSON.stringify(state.layout));renderLayoutOptions();$("layoutSelect").value=name;showToast(`Saved ${name}`);}
function renderLayoutOptions(){const select=$("layoutSelect");select.innerHTML="";Object.keys(state.saved).sort().forEach((name)=>{const option=document.createElement("option");option.value=name;option.textContent=name;select.appendChild(option);});if(!select.options.length){const option=document.createElement("option");option.textContent="No saved layouts";option.disabled=true;select.appendChild(option);}}
function bindLayoutManager(){$("saveNamed").addEventListener("click",saveNamed);$("saveLayout").addEventListener("click",()=>{$("layoutName").value=$("layoutName").value.trim()||"My layout";saveNamed();});$("layoutSelect").addEventListener("change",(event)=>{if(state.saved[event.target.value]){state.layout=normalizeLayout(clone(state.saved[event.target.value]));state.selected=null;render();}});$("deleteLayout").addEventListener("click",()=>{const name=$("layoutSelect").value;if(state.saved[name]){delete state.saved[name];persistSaved();renderLayoutOptions();showToast("Layout deleted");}});}
function bindTemplates(){document.querySelectorAll("[data-template]").forEach((el)=>el.addEventListener("click",()=>{state.layout=normalizeLayout({elements:clone(templates[el.dataset.template])});state.selected=null;render();showToast(`${el.textContent.trim()} template loaded`);}));$("addButton").addEventListener("click",()=>{const item=button("Button",430,250);state.layout.elements.push(item);state.selected=item.id;render();});}
$("previewToggle").addEventListener("click",()=>{state.preview=!state.preview;state.selected=null;state.guides=[];render();});bindInspector();bindLayoutManager();bindTemplates();loadSaved();render();
