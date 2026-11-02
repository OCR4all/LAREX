import { HL } from './kernel.js'

/** An open, empty binder: two hinged covers, a spine mechanism and four bare rings. */
const {
  Cam, clamp, fit, proj, rad, rrect, run, poly, open, hull, spring, stepS,
  mk, pointer, register, disposer
} = HL

export function mountRingBinder({ stage, svg, read }, value) {
  const bag = disposer()
  const C = Cam(45, 0.5, 1.62)
  const transform = (x, y, z, px, ry) => {
    const a = rad(px), b = rad(ry)
    const yy = y * Math.cos(a) - (z - 9) * Math.sin(a)
    const zz = y * Math.sin(a) + (z - 9) * Math.cos(a)
    return [x * Math.cos(b) + zz * Math.sin(b), yy,
      22 + zz * Math.cos(b) - x * Math.sin(b)]
  }
  const bounds = []
  for (const px of [-28, -3, 22]) for (const ry of [-23, 2, 27]) {
    for (const x of [-72, 72]) for (const y of [-48, 48]) {
      bounds.push(transform(x, y, 9, px, ry), transform(x, y, 43, px, ry))
    }
  }
  fit(C, bounds, 200, 166)
  const P = proj(C)
  const g = mk('g', {}, svg)
  const pitch = spring(-3), roll = spring(2)
  const left = spring(13), right = spring(21)
  const springs = [pitch, roll, left, right]
  let reach = value, over = null, drawn = ''

  // Rounded boards, each with one inset crease and a thin lower edge.
  const board = (a, b) => ({
    outline: rrect(a, -48, b, 48, 4.5, 8),
    inset: rrect(a + 2, -46, b - 2, 46, 3, 8),
    back: mk('path', { class: 'lo' }, g),
    face: mk('path', { class: 'sil' }, g),
    crease: mk('path', { class: 'nf lo' }, g)
  })
  const l = board(-72, -7)
  const spine = {
    outline: rrect(-7, -48, 7, 48, 2, 6),
    face: mk('path', { class: 'sil' }, g),
    crease: mk('path', { class: 'nf lo' }, g)
  }
  const r = board(7, 72)
  const mechanism = mk('path', { class: 'sil' }, g)
  const seam = mk('path', { class: 'nf lo' }, g)
  const rings = [-30, -10, 10, 30].map(y => ({
    y, path: mk('path', { class: 'sil' }, g),
    join: mk('path', { class: 'nf lo' }, g)
  }))
  const feet = [-39, 39].map(y => ({ y, path: mk('path', { class: 'nf' }, g) }))

  // Local points rotate about the spine, then the whole binder tips as one object.
  function world(x, y, z, px = pitch.x, ry = roll.x) {
    return P(...transform(x, y, z, px, ry))
  }
  function coverPoint(q, side, angle, thickness = 0) {
    const hinge = side * 7, d = q.u - hinge, a = rad(angle)
    return world(hinge + d * Math.cos(a), q.v,
      9 + Math.abs(d) * Math.sin(a) - thickness)
  }
  function drawBoard(board, side, angle) {
    const top = board.outline.map(q => coverPoint(q, side, angle))
    const bottom = board.outline.map(q => coverPoint(q, side, angle, 1.7))
    board.back.setAttribute('d', poly(hull([...top, ...bottom])))
    board.face.setAttribute('d', poly(top))
    // The near half of the inset is the board's single quiet crease.
    board.crease.setAttribute('d', open(run(board.inset, q => q.nu + q.nv > 0)
      .map(q => coverPoint(q, side, angle))))
  }
  function ringPoints(y, radius, start = 0, end = Math.PI * 2) {
    return Array.from({ length: 81 }, (_, i) => {
      const a = start + (end - start) * i / 80
      return world(radius * Math.cos(a), y, 20.2 + radius * Math.sin(a))
    })
  }
  function draw() {
    const key = springs.map(s => s.x).join(',')
    if (key === drawn) return
    drawn = key
    drawBoard(l, -1, left.x)
    spine.face.setAttribute('d', poly(spine.outline.map(q => world(q.u, q.v, 9))))
    spine.crease.setAttribute('d', open([world(-5, 44, 9.2), world(-5, -44, 9.2)]))
    drawBoard(r, 1, right.x)
    mechanism.setAttribute('d', poly(rrect(-4, -41, 4, 41, 3, 8)
      .map(q => world(q.u, q.v, 10.5))))
    seam.setAttribute('d', open([world(0, -37, 10.6), world(0, 37, 10.6)]))
    // Annular opaque wire: the reversed inner loop leaves the ring genuinely empty.
    rings.forEach((item) => {
      item.path.setAttribute('d', poly(ringPoints(item.y, 9.5))
      + poly(ringPoints(item.y, 8).reverse()))
      item.join.setAttribute('d', open([world(-0.7, item.y, 28.9), world(0.7, item.y, 28.9)]))
    })
    feet.forEach(item => item.path.setAttribute('d', poly(rrect(-2, item.y - 1, 2, item.y + 1, 0.8, 5)
      .map(q => world(q.u, q.v, 10.7)))))
  }
  const B = register(stage, (dt) => {
    let moving = false
    springs.forEach((s) => {
      if (stepS(s, dt)) moving = true
    })
    draw()
    return moving
  })
  bag.add(B.unregister)
  const centres = rings.map(item => world(0, item.y, 25.2, -3, 2))
  function retarget() {
    if (!over) {
      pitch.t = -3
      roll.t = 2
      left.t = 13
      right.t = 21
      rings.forEach((item, i) => item.path.classList.toggle('hi', i === 1))
      read.textContent = 'rest'
    } else {
      const dx = clamp((over[0] - 200) / 125, -1, 1)
      const dy = clamp((over[1] - 166) / 100, -1, 1)
      // Convert the screen direction into the two fixed isometric ground axes.
      pitch.t = -3 + reach * (dy - dx) * 0.5
      roll.t = 2 - reach * (dy + dx) * 0.5
      left.t = 13 + 7 * (1 - dx) / 2 + 2 * dy
      right.t = 21 + 7 * (1 + dx) / 2 - 2 * dy
      const nearest = centres.reduce((best, p, i) =>
        Math.hypot(p[0] - over[0], p[1] - over[1])
        < Math.hypot(centres[best][0] - over[0], centres[best][1] - over[1])
          ? i
          : best, 0)
      rings.forEach((item, i) => item.path.classList.toggle('hi', i === nearest))
      read.textContent = `ring ${nearest + 1} · 0`
    }
    B.wake()
  }
  bag.add(pointer(stage, {
    move: (p) => {
      over = p
      retarget()
    },
    leave: () => {
      over = null
      retarget()
    }
  }))
  bag.add(() => svg.replaceChildren())
  draw()
  retarget()
  return {
    set: (v) => {
      reach = v
      retarget()
    },
    destroy: bag.dispose
  }
}
