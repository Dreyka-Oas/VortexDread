#if !defined INCLUDE_VORTEXDREAD_DECK
#define INCLUDE_VORTEXDREAD_DECK

/*
  One storm, written into the cloud layer the pack already draws.

  Nothing here marches a volume of its own and nothing here carries a field of noise of its own. The
  mesocyclone is the pack's cumulus deck with its gaps filled and its base let down. The tornado under
  it is that same deck pulled through the floor of the layer: a sample below the cloud base is answered
  by moving it back up into the piece of deck it came out of and asking the pack what is there, so the
  column arrives with the pack's own grain, its own erosion, its own lighting, its own self shadowing,
  its own aerial perspective and its own lightning, for the price of a rotation and a divide.

  Reading the deck at a squeezed radius is also where the look comes from. A disc of cloud as wide as
  the wall cloud is folded into a column a fraction of that across, so a lump up there arrives as a
  vertical streak down here, and winding the fold as it descends lays those streaks into a helix. That
  is what a funnel is made of, and it is the one structure a separate noise field never gets right.
*/

#include "/include/vortexdread/state.glsl"

// Ranges the mod folded each field into before it fit them into bytes.
const float vortexdread_reach = 2048.0;
const float vortexdread_ground_span = 1152.0;
const float vortexdread_ground_floor = -128.0;
const float vortexdread_radius_span = 128.0;

// The column's radius where it meets the ground and where it meets the cloud, in core radii, and how
// late it opens out. A funnel stands nearly straight through the middle of its height and flares over
// the last third, where the inflow is still spread wide. A straight cone is the one silhouette nothing
// in the sky has.
const float vortexdread_foot_span = 0.95;
const float vortexdread_crown_span = 3.1;
const float vortexdread_taper = 2.6;

// The spread in the last few metres, where the surface stops the inflow going any further down and it
// has to turn outward.
const float vortexdread_skirt = 0.28;

// Tangential speed at the condensation wall, in blocks per second, at the storm's own peak wind. The
// turn rate everywhere else follows from it and from the radius there, so a narrow rope spins fast and
// a wedge turns slowly, which is the difference between the two on any footage of them.
const float vortexdread_peak_speed = 42.0;

// Turns the column is already wound by over its own height. Rotation with time is what makes it move;
// this is what makes it look twisted when it is not moving.
const float vortexdread_wound = 2.2;

// Where in the layer a column sample reads its cloud from, at the ground and at the cloud base, on the
// pack's own height scale. Two levels rather than one, so the column is not a single slice of deck
// smeared down its whole length: as it falls it draws from further under the deck and the pattern
// changes with it. On the pack's scale rather than on the layer's depth, because the pack stretches
// that scale by how much cloud the weather asked for, and a level fixed in metres lands somewhere
// different every day.
const float vortexdread_source_low = 0.30;
const float vortexdread_source_high = 0.56;

// The widest ring of deck the column is allowed to read, in cloud units.
//
// A ceiling rather than a floor, and it took several passes to work out which way round it went. The
// lumps in a cumulus deck are two to four hundred blocks across, so a column reading a ring of its own
// width gets three or four of them around its circumference and comes out as a bunch of cauliflower
// hanging off the cloud, which is worse than the smooth cone it replaced. Held under this, the deck
// arrives nearly even and the shape is the column's own; the grain comes from the pack's two worley
// passes below, which run at twenty to a hundred blocks and are the right size for a funnel to begin
// with. The read is still what makes this the pack's cloud rather than a shape drawn beside it: same
// material, same light, same fog, same lightning.
const float vortexdread_read_ring = 450.0;

// How far the level a sample reads from wanders around the column, on the pack's own height scale.
//
// The fold alone only works where the deck happens to be lumpy, and a deck that is uniformly thick over
// the storm is exactly where it fails. Drawing from a different depth at each angle finds contrast that
// is always there, since a cloud layer is never the same density top to bottom, and winding the wander
// with the descent lays it into the same helix everything else here follows.
const float vortexdread_source_wobble = 0.10;

// How much of the deck's own gaps the column closes, and the most any sample of it carries.
//
// Closed part of the way rather than filled, and this is the difference between a funnel and a pipe.
// Condensation in a vortex is continuous where a cloud is not, so the holes want shutting; take them
// all the way shut and every sample comes back at the ceiling, the detail passes below have no headroom
// left to carve, and what the march returns is a solid with straight sides.
const float vortexdread_column_knit = 0.70;
const float vortexdread_column_cap = 0.92;

// How much of the middle is cleared out well up the column. Down where the inflow arrives it stays
// full: a funnel drawn as a tube to the ground shows daylight through itself, which no photograph of
// one does.
const float vortexdread_column_hollow = 0.5;

// How torn the tip is, as a share of the ground to base drop.
const float vortexdread_tatter = 0.05;

// How much light a column sample is allowed to give back, at the cloud base and at the ground.
//
// The pack lights a cloud as though the sky above it were open, which is true of the deck and is the
// opposite of true two hundred metres under its base. Everything reaching a funnel has already been
// through the storm, and that is why a photographed one is darker than the cloud it hangs from however
// bright the day is. The light march does find that depth, but most of what lights a thick cloud here
// is the multiple scattering tail, and the tail is shadowed by almost nothing on purpose.
// Spent over the whole drop rather than at one end of it, because that gradient is what says the thing
// is standing in the air: a column of one brightness top to bottom is a cut-out, whatever shade it is.
const float vortexdread_gloom_high = 0.70;
// The foot, and it has a floor under it that is not about realism. Past here the column gives back so
// little that the composite falls through to its own sky term and the pack's metering opens on a frame
// with a hole in it: measured against the sky beside it the funnel comes out paler at a twentieth than
// it does at an eighth, which is the opposite of what asking for less light is supposed to buy.
const float vortexdread_gloom_low = 0.12;

// The same debt, owed by the storm's own base rather than by the funnel under it.
//
// Ten kilometres of tower stand between a supercell base and the sun, which is why the underside of one
// photographs near black on the brightest afternoon of the year. The pack has no way to know that: it
// lights every cloud as though the sky above it were open, so an organised deck comes out the pale grey
// of ordinary overcast and the storm reads as weather that happens to be in the way. Spent by how much
// storm stands over the sample and how far under the crest it sits, so the anvil keeps its white while
// the base loses it, which is the one contrast that says supercell from any distance.
const float vortexdread_gloom_deck = 0.30;

// The same debt again, owed by every cloud the storm is standing among, and paid over a much wider
// circle than the storm itself covers.
//
// An anvil throws its shadow tens of kilometres downwind, so the ordinary cumulus either side of a
// supercell is under it too and is not the white it would be on a fair day. Left out, the frame holds a
// black storm sitting in a field of bright fair-weather cloud, and what the eye takes from that is not
// a big storm, it is a dark patch pasted onto a nice afternoon. How far it reaches is in mesocyclone
// radii, counted from the axis.
const float vortexdread_gloom_anvil = 0.85;
const float vortexdread_gloom_span = 5.0;

// Radius of the organised deck, in blocks. Smaller than a real mesocyclone: the pack hangs its cloud
// layer two hundred blocks up, so a disc as wide as the real thing would fill the sky from horizon to
// horizon and read as overcast. What a player has to be able to do is stand outside it and see its
// shape.
const float vortexdread_meso = 340.0;

// The lowering, in core radii and at least this many blocks across. A rope hangs under a wall cloud as
// wide as a wedge's, because what sets that width is the updraft above it rather than the thread that
// came out of the bottom. How far up the layer it reaches is the other half: a wall cloud is a bulge
// on the underside of the base, and taken through the whole depth instead it is a cylinder with hard
// sides, which from any distance is a rectangle standing in the sky.
const float vortexdread_wall_span = 8.0;
const float vortexdread_wall_floor = 110.0;

// The widest that lowering is ever allowed to be, as a share of the mesocyclone.
//
// A wall cloud is a kilometre or two across whether the tornado under it is a rope or a wedge, so a
// radius taken straight off the core overshoots badly at the top of the scale: an EF5 ends up with a
// lowering wider than the storm holding it, the dark base covers the whole sky, and a frame with no
// light left anywhere in it is one the pack's own exposure cannot rescue.
const float vortexdread_wall_cap = 0.85;
const float vortexdread_wall_deep = 0.30;

// The tower's radius where the air comes in and where it spreads, in mesocyclone radii, and where on
// the pack's own height scale it is widest and gone. That scale is not the layer: the pack stretches it
// by how much cloud the weather asked for, and it runs past one on a thin day, which is where the pack
// stops drawing. Both numbers sit under that, so the storm ends inside the cloud rather than at the
// edge of the slab holding it.
const float vortexdread_foot_reach = 0.55;
const float vortexdread_anvil_reach = 1.40;
const float vortexdread_crest = 0.50;
const float vortexdread_ceil_fade = 0.86;

// How much coverage the storm adds under the tower and under the lowering, and the most any sample is
// allowed to carry. Added to the field the pack already built rather than mixed toward one, because the
// detail passes underneath carve in proportion to the headroom left: a sample taken to full gives them
// nothing to bite on and comes back out as a solid.
const float vortexdread_fill = 0.60;
const float vortexdread_wall_fill = 0.72;
const float vortexdread_ceiling = 0.66;

// How much of the pack's taper toward the floor of the layer the storm is let off. Not all of it: even
// a storm base is thinner at its very edge than it is a hundred metres higher, and a base that goes
// flat and opaque at the exact bottom of the layer draws a plane rather than a cloud.
const float vortexdread_base_hold = 0.70;

// Where the base hangs, as a fraction of the layer, under the lowering and under the rest of the storm.
// The pack starts every cloud a fifth of the way up its layer, so anything under that is a base
// hanging below the one beside it, which is what a lowering is to look at.
const float vortexdread_hang = 0.03;
const float vortexdread_meso_hang = 0.10;

// How thick the underside of that base is, on the same scale.
//
// The pack's own base is soft because its height wanders with the noise it was cut from, and a height
// the storm sets does not wander at all. Cut at one level over a disc six hundred blocks across, it is
// not a cloud, it is a table hanging in the sky with a tornado under it, and the straight edge shows
// from any distance.
const float vortexdread_underside = 0.13;

// And how far that height wanders from place to place, on the same scale again.
//
// The rim of a wall cloud is broken on its radius already, which is what its plan view needs and what
// its silhouette never sees. Seen from below and from far enough away, a disc six hundred blocks across
// is edge on, so the only thing the eye is reading is the height of its underside, and a height that is
// the same everywhere draws a spirit level across the sky. Lumps a few hundred blocks wide put the
// scalloped underside back, which is the shape everyone recognises without being able to name.
const float vortexdread_lip = 0.13;

// How far out the storm clears the sky, as a multiple of the mesocyclone radius, and how much of the
// layer it takes there. A storm draws its air in from a long way around, and air going up over there is
// not making cloud here: the ring of thinned deck outside a supercell is the reason the mass in the
// middle reads as a mass at all. Under a sky the game has already taken to full coverage it is the only
// thing that can, since a layer at full coverage has nothing left to add.
const float vortexdread_clear = 2.4;
const float vortexdread_slot = 0.65;

// Turn rate of the deck at the edge of the mesocyclone, in radians per second, falling off with the
// square of the distance outside it. A wall cloud takes minutes to come round once, and the thing that
// sells its rotation is the shear between the rim and the middle rather than the speed of either.
const float vortexdread_deck_spin = 0.016;

// The storm, in the frame and the units the cloud functions work in.
bool vortexdread_live = false;
vec2 vortexdread_axis = vec2(0.0);
float vortexdread_ground = 0.0;  // radius of the ground under the foot
float vortexdread_core = 0.0;    // core radius
float vortexdread_drop = 0.0;    // ground to cloud base
float vortexdread_descent = 0.0; // how far down out of the cloud it has come, 0 to 1
float vortexdread_load = 0.0;    // how much ground it has in the air
float vortexdread_wind = 0.0;    // its turn against the fastest it will ever turn

// Set by the warp, read by everything the pack does to a sample afterwards. A column sample has already
// been moved and turned, so the deck's own rotation must not be applied to it twice, and the pack's
// taper toward the floor of the layer is replaced by the column's own profile rather than dropped: that
// taper is the line restoring the contrast the edge sharpening just flattened, so a sample that skips it
// comes back near one whatever it was, and a march of those is a white slab with straight sides.
float vortexdread_column = 0.0;

// How far below the cloud base a column sample sits, zero at the base and one at the ground.
float vortexdread_under = 0.0;

// Where in the layer a column sample read from, on the pack's own altitude scale.
//
// The march works this number out from the sample's own height, which for anything under the floor of
// the layer is negative, and one of the terms it feeds is an optical depth that goes into exp(-k * x).
// A negative depth there is a gain of e to the power of a thousand, which is a white disc the size of
// the storm and an aperture that shuts on the rest of the picture to compensate.
float vortexdread_source_level = 0.0;

// How much organised storm the density function last answered for, and how far under its crest that
// sample sat. Read by the gloom, which needs both and can recompute neither: the position it was given
// is gone by then and the light march has run its own density calls over the top of it.
float vortexdread_deck_hold = 0.0;
float vortexdread_deck_reach = 0.0;
float vortexdread_deck_depth = 0.0;

float vortexdread_unpack(float low, float high) {
    return (low * 255.0 + high * 255.0 * 256.0) * (1.0 / 65535.0);
}

/**
 * Reads the storm once, into the frame the cloud functions work in.
 *
 * <p>Called before the march rather than inside it. The state is eight texels wide and the march is
 * seventy steps deep, so reading it per sample would cost more than everything else in the cloud pass
 * put together, and it cannot change between two samples of the same ray anyway.
 */
void vortexdread_gather(vec3 air_viewer_pos) {
    vortexdread_live = texelFetch(vortexdread_state, ivec2(5, 0), 0).x > 0.5;
    if (!vortexdread_live) {
        return;
    }

    vec4 place = texelFetch(vortexdread_state, ivec2(0, 0), 0);
    vec4 size = texelFetch(vortexdread_state, ivec2(1, 0), 0);
    vec4 reach = texelFetch(vortexdread_state, ivec2(2, 0), 0);
    vec4 colour = texelFetch(vortexdread_state, ivec2(3, 0), 0);
    vec4 light = texelFetch(vortexdread_state, ivec2(4, 0), 0);

    vec2 offset = vec2(
        vortexdread_unpack(place.x, place.y) * 2.0 * vortexdread_reach - vortexdread_reach,
        vortexdread_unpack(place.z, place.w) * 2.0 * vortexdread_reach - vortexdread_reach
    );
    float ground_y =
        vortexdread_unpack(size.x, size.y) * vortexdread_ground_span + vortexdread_ground_floor;

    vortexdread_axis = air_viewer_pos.xz + offset * CLOUDS_SCALE;
    vortexdread_ground = planet_radius + CLOUDS_SCALE * (ground_y - SEA_LEVEL);
    vortexdread_core = vortexdread_unpack(size.z, size.w) * vortexdread_radius_span * CLOUDS_SCALE;
    vortexdread_descent = vortexdread_unpack(reach.z, reach.w);
    vortexdread_load = colour.a;
    vortexdread_wind = vortexdread_unpack(light.z, light.w);

    // The column ends at the layer the pack is actually drawing rather than at the height the mod
    // thinks the cloud base is. Taking the pack's own number is what keeps the funnel touching the
    // cloud whatever altitude the player has left the pack's own setting on.
    vortexdread_drop = clouds_cumulus_radius - vortexdread_ground;
    vortexdread_live = vortexdread_core > 1.0 && vortexdread_drop > 1.0;
}

/** Radius of the column at a height fraction, 0 at the ground and 1 against the cloud base. */
float vortexdread_column_radius(float h) {
    float shape = vortexdread_foot_span
        + (vortexdread_crown_span - vortexdread_foot_span) * pow(h, vortexdread_taper);
    shape += vortexdread_skirt * (1.0 - smoothstep(0.0, 0.07, h));
    return vortexdread_core * shape;
}

/** The widest the column ever gets, which is what the march has to bound. */
float vortexdread_column_reach() {
    return vortexdread_core * vortexdread_crown_span * 1.25;
}

/**
 * Where along a view ray the column stands, so the march can be extended under the cloud base there
 * and nowhere else.
 *
 * <p>Returned as a span rather than a flag: the deck is a shell the pack already knows how to walk, and
 * the column hangs below its floor, so what the march wants is the union of the two and not a second
 * loop. A ray that misses the column gets an empty span and the pack is left exactly as it was.
 */
vec2 vortexdread_column_span(vec3 ray_origin, vec3 ray_dir) {
    if (!vortexdread_live) {
        return vec2(1.0, -1.0);
    }

    // Between the ground the storm stands on and the floor of the cloud layer.
    vec2 shell = intersect_spherical_shell(
        ray_origin, ray_dir, vortexdread_ground, clouds_cumulus_radius);
    if (shell.y < 0.0) {
        return vec2(1.0, -1.0);
    }

    float radius = vortexdread_column_reach();
    vec2 from_axis = ray_origin.xz - vortexdread_axis;
    float a = dot(ray_dir.xz, ray_dir.xz);
    float b = 2.0 * dot(from_axis, ray_dir.xz);
    float c = dot(from_axis, from_axis) - radius * radius;

    float near;
    float far;
    if (a < 1.0e-9) {
        // Straight up or straight down: either the whole ray is inside the column or none of it is.
        if (c > 0.0) {
            return vec2(1.0, -1.0);
        }
        near = 0.0;
        far = 1.0e9;
    } else {
        float disc = b * b - 4.0 * a * c;
        if (disc < 0.0) {
            return vec2(1.0, -1.0);
        }
        float root = sqrt(disc);
        near = (-b - root) / (2.0 * a);
        far = (-b + root) / (2.0 * a);
    }

    return vec2(max(max(near, shell.x), 0.0), min(far, shell.y));
}

// The column's own walk, planned once per ray so the march can take it a step at a time.
uint vortexdread_walk_steps = 0u;
float vortexdread_walk_start = 0.0;
float vortexdread_walk_step = 0.0;

/**
 * How the column is walked, alongside the walk the pack already makes through the deck.
 *
 * <p>Two spacings rather than one, and this is the whole reason. The deck is kilometres of low contrast
 * cloud and a step of twenty blocks resolves it. The column is a wall a few blocks thick, and a step of
 * twenty walks straight past it. Marching the union of the two at the column's spacing would multiply
 * the cost of every ray in the sky by ten; marching it at the deck's draws a staircase where the funnel
 * should be. Worse than either, changing the deck's spacing on the rays that cross the column makes the
 * cylinder bounding it draw its own outline across the sky, which is a rectangle in a cloud.
 *
 * <p>So the loop runs the column's samples first and the deck's after, each at its own length, and the
 * only thing the two share is the accumulator. The column first because it is nearer than the cloud
 * base from anywhere a player stands. Seen from above the layer the two swap over, which is the one
 * case this has in the wrong order, and from up there the funnel is inside its own wall cloud anyway.
 *
 * @param span  where the column stands on this ray
 * @param reach how far the ray gets before something solid stops it
 */
void vortexdread_plan_walk(vec2 span, float reach) {
    vortexdread_walk_steps = 0u;
    if (!vortexdread_live) {
        return;
    }

    float far = min(span.y, reach);
    if (far <= span.x) {
        return;
    }

    // Sized off the wall rather than off the span, since what the march must not miss is the sheath.
    float wanted = (far - span.x) / max(vortexdread_core * 0.22, 1.0);
    vortexdread_walk_steps = uint(clamp(wanted, 10.0, 64.0));
    vortexdread_walk_start = span.x;
    vortexdread_walk_step = (far - span.x) / float(vortexdread_walk_steps);
}

/**
 * A sample under the cloud base, moved to the piece of deck it came out of.
 *
 * <p>The column is pinched, so a ring of air down here filled a wider ring of the deck up there: the
 * offset from the axis is opened out by that ratio before it is read. And it is wound, by an angle that
 * grows as it falls and grows faster where it is tighter, which is angular momentum doing what it does
 * and also what lays the streaks into a helix.
 *
 * <p>Writes the sample's height back into {@code r} so everything the pack does afterwards believes it
 * is looking at ordinary deck, and returns how much of the column stands here, which is zero outside it
 * and outside a storm.
 */
float vortexdread_warp(inout vec3 pos, inout float r) {
    vortexdread_column = 0.0;
    vortexdread_under = 0.0;
    vortexdread_source_level = 0.0;

    if (r >= clouds_cumulus_radius) {
        return 1.0;
    }
    if (!vortexdread_live) {
        return 0.0;
    }

    float h = (r - vortexdread_ground) / vortexdread_drop;
    if (h < 0.0 || h > 1.0) {
        return 0.0;
    }

    vec2 from_axis = pos.xz - vortexdread_axis;
    float d = length(from_axis);
    float around = atan(from_axis.y, from_axis.x);

    // The sheath is torn into sheets that wrap round the column and slide up it, so its outline is never
    // a circle and never the same twice. Written on the radius rather than on the density: at this
    // thickness the inside is opaque from any angle, so the outline is the whole of what there is to see
    // of the shape, and a circle is what made the thing read as a machined part.
    float wall = vortexdread_column_radius(h) * (1.0
        + 0.15 * sin(around * 4.0 + h * 9.0 - frameTimeCounter * 0.7)
        + 0.09 * sin(around * 7.0 - h * 16.0 + frameTimeCounter * 1.3)
        + 0.05 * sin(around * 13.0 + h * 27.0 - frameTimeCounter * 2.1));
    if (d > wall) {
        return 0.0;
    }

    // How far down out of the cloud the funnel has come. A young one is a stub hanging off the base and
    // nothing below it, so the bottom of the span has to be cut rather than faded over the whole length.
    // Cut on a torn ring rather than a level, and turned with the vortex, because the tip of a funnel is
    // the one part of it that is never flat and never still.
    //
    // The tear straddles the contact rather than sitting above it. Added to a fully descended column it
    // takes the cut up to a seventh of the drop, which on a two hundred block base is a funnel ending
    // thirty blocks in the air: from any distance that is a shape hanging in the sky, and the one thing
    // everyone knows about a tornado is that it is touching the ground.
    float tip = (1.0 + vortexdread_tatter) * (1.0 - vortexdread_descent) - vortexdread_tatter
        + vortexdread_tatter * (sin(around * 3.0 + frameTimeCounter * 0.9)
            + 0.55 * sin(around * 7.0 - frameTimeCounter * 1.7));
    float reached = smoothstep(tip - 0.06, tip + 0.06, h);
    if (reached < 0.004) {
        return 0.0;
    }

    float squeeze = vortexdread_column_radius(1.0) / wall;

    // One rate for the whole column rather than one per height, and this is not a simplification.
    // Angular speed really does rise as the radius falls, but a rotation whose rate varies with height
    // shears the column without any limit: a minute in, the foot and the crown are turned hundreds of
    // radians apart, two samples a few blocks apart on the same ray read opposite sides of the deck, and
    // what the march returns is a stack of unrelated slices. That is the thing that comes apart into
    // lumps after a while and looks fine on the first screenshot. The shear that is real is the winding
    // below, which is fixed and therefore stays a shape.
    float spin = vortexdread_peak_speed * CLOUDS_SCALE * (0.35 + 0.65 * vortexdread_wind)
        / vortexdread_column_radius(1.0);
    float turn = spin * frameTimeCounter + vortexdread_wound * (1.0 - h) * squeeze;

    float c = cos(turn);
    float s = sin(turn);
    float reach_out = min(1.0, vortexdread_read_ring / vortexdread_column_radius(1.0));
    vec2 wide = from_axis * squeeze * reach_out;
    pos.xz = vortexdread_axis + vec2(wide.x * c - wide.y * s, wide.x * s + wide.y * c);

    // Read from further under the deck the lower the sample is, so the column is not one slice of cloud
    // smeared down its whole length.
    float level = mix(vortexdread_source_low, vortexdread_source_high, h)
        + vortexdread_source_wobble * sin(around * 3.0 + h * 11.0 - turn);
    vortexdread_source_level = level;
    float target = clouds_cumulus_radius + level * rcp(clouds_params.l0_altitude_scale);
    pos.y = sqrt(max(target * target - dot(pos.xz, pos.xz), 0.0));
    r = target;

    // A funnel is a sheath rather than a cone of fog: water condenses where the pressure drop is
    // steepest, which is at the wall, and the middle of a mature one is comparatively clear. Only well
    // up it, though, and only when there is no ground in the air to fill it.
    float across = d / wall;
    float edge = smoothstep(1.0, 0.72, across);
    float hollow = mix(
        1.0,
        smoothstep(0.15, 0.55, across),
        vortexdread_column_hollow * smoothstep(0.3, 0.95, h) * (1.0 - vortexdread_load));

    vortexdread_column = edge * hollow * reached;
    vortexdread_under = 1.0 - h;
    return vortexdread_column;
}

// How far the light reaching a column sample has had to come, as a share of one full traverse of the
// deck, at the cloud base and again at the ground. The same for the sky term, which arrives from all
// round and therefore through less of it.
//
// Marched rather than guessed everywhere else in the pack, and this is the one place the march cannot
// answer. A light ray leaves a funnel sideways and lands in whatever gap of deck happens to be over it,
// so two samples a few blocks apart come back hundreds of units apart: the column is lit in patches,
// bright where a gap lined up and dark between, and the shape disappears into its own lighting. Under a
// storm base there are no gaps. Everything reaching a funnel has come through the whole depth of the
// cloud above it and then through the rain under that, which is a long path and an even one.
//
// Skipping the two marches is worth as much as correcting them: ten density calls per column sample of
// every ray that crosses the funnel, spent on a number that was wrong.
const float vortexdread_light_base = 0.22;
const float vortexdread_light_foot = 0.60;
const float vortexdread_sky_base = 0.16;
const float vortexdread_sky_foot = 0.45;

/** Light path length for the column sample the density function last answered. */
float vortexdread_column_light_depth() {
    return clouds_cumulus_thickness
        * mix(vortexdread_light_base, vortexdread_light_foot, vortexdread_under);
}

/** The same, for what arrives from the sky rather than from the sun. */
float vortexdread_column_sky_depth() {
    return clouds_cumulus_thickness
        * mix(vortexdread_sky_base, vortexdread_sky_foot, vortexdread_under);
}

/**
 * How much light a column sample is allowed to give back.
 *
 * <p>One everywhere else. The pack lights a cloud as though the sky above it were open, which is true of
 * the deck and is the opposite of true two hundred metres under its base: everything reaching a funnel
 * has already come through the storm, and that is why a photographed one is darker than the cloud it
 * hangs from however bright the day is. The light march does find that depth, but almost all of what
 * lights a thick cloud here is the multiple scattering tail and the tail is deliberately shadowed by
 * next to nothing.
 *
 * <p>Read straight after the density call that set it, since the light march runs its own density calls
 * in between and each of those leaves the flag behind.
 */
float vortexdread_gloom() {
    if (vortexdread_column > 0.0) {
        // Handed back to the deck's own figure at the very top. The column ends at the floor of the
        // layer whatever else is going on, so a funnel lit differently from the cloud it hangs out of
        // draws a horizontal line across the sky at the exact height of the cloud base.
        float own = mix(vortexdread_gloom_high, vortexdread_gloom_low, vortexdread_under);
        return mix(vortexdread_gloom_deck, own, smoothstep(0.0, 0.25, vortexdread_under));
    }
    float around = mix(1.0, vortexdread_gloom_anvil, vortexdread_deck_reach);
    return mix(around, vortexdread_gloom_deck, vortexdread_deck_hold * vortexdread_deck_depth);
}

struct VortexdreadDeck {
    float meso; // how much of the rotating storm stands over this column of air
    float edge; // distance to the axis, in mesocyclone radii
    float wall; // how far into the lowering under the funnel
    float slot; // how much of the ring of thinned sky outside the storm falls here
    float spin; // radians the cloud field is turned by here
    float lip;  // how far this patch of underside hangs below the rest, as a share of the layer
};

/** What the storm does to one horizontal position, in the frame the cloud functions work in. */
VortexdreadDeck vortexdread_deck_at(vec2 cloud_xz) {
    VortexdreadDeck deck = VortexdreadDeck(0.0, 1e6, 0.0, 0.0, 0.0, 0.0);
    if (!vortexdread_live) {
        return deck;
    }

    vec2 from_axis = (cloud_xz - vortexdread_axis) / CLOUDS_SCALE;
    float d = length(from_axis);

    // A young mesocyclone has been turning for minutes before anything comes out of it, so the deck is
    // already organised while the descent is still near zero. It only ever gets more so.
    float grown = 0.45 + 0.55 * vortexdread_descent;

    // Nothing about a storm is round, and a rim taken straight off the distance draws the one silhouette
    // that gives the whole thing away: a smooth symmetrical disc with a funnel in the middle of it. Broken
    // on the radius rather than on the density, so what moves is the edge; thinning the middle instead
    // would put holes through the mass this pass exists to build. Carried round with the deck's own turn,
    // because a lobe that stays put while the cloud goes past it is a shape painted on the sky.
    float around = atan(from_axis.y, from_axis.x)
        + vortexdread_deck_spin * frameTimeCounter * grown;
    float ragged = 1.0 + 0.17 * sin(around * 3.0)
        + 0.11 * sin(around * 5.0 + 2.3)
        + 0.06 * sin(around * 11.0 - 1.1);
    float rough = d / ragged;

    // How far the storm reaches, faded rather than switched. The rim proper is cut in the shaping, where
    // the height is known; what is wanted here is the widest the tower ever gets, and a hard cut at that
    // radius would draw its own edge in the sky a long way outside the cloud.
    deck.edge = rough / vortexdread_meso;
    deck.meso = (1.0 - smoothstep(vortexdread_anvil_reach * 1.05,
                                  vortexdread_anvil_reach * 1.45, deck.edge)) * grown;

    float lowering = clamp(vortexdread_core / CLOUDS_SCALE * vortexdread_wall_span,
                           vortexdread_wall_floor,
                           vortexdread_meso * vortexdread_wall_cap);
    deck.wall = (1.0 - smoothstep(lowering * 0.35, lowering * 1.15, rough)) * grown;

    // The thinned ring: everything the storm has drawn its air from, which is everything out to the far
    // radius except what the storm itself stands on.
    float ring = 1.0 - smoothstep(vortexdread_meso * vortexdread_anvil_reach,
                                  vortexdread_meso * vortexdread_clear, rough);
    deck.slot = ring
        * smoothstep(vortexdread_foot_reach, vortexdread_anvil_reach, deck.edge) * grown;

    // Angular momentum outside the rim, solid body inside it. Turning the whole block as one piece would
    // read as a spinning texture; faster toward the middle is what a cyclone does and what curves the
    // bands.
    float rim = max(d / vortexdread_meso, 1.0);
    deck.spin = vortexdread_deck_spin * frameTimeCounter * deck.meso / (rim * rim);

    // Three lumps, roughly five hundred, three hundred and two hundred blocks across, carried round with
    // the turn like the rim is so the underside is not a pattern the storm rotates underneath.
    vec2 turned = vec2(from_axis.x * cos(deck.spin) - from_axis.y * sin(deck.spin),
                       from_axis.x * sin(deck.spin) + from_axis.y * cos(deck.spin));
    // Lifted rather than swung, since the lowering already sits on the floor of the layer and a rim
    // pushed below it is not a deeper scallop, it is a plate with a straight edge on both sides.
    deck.lip = vortexdread_lip * (0.5 + 0.5 * (0.55 * sin(turned.x * 0.012 + around * 2.0)
        + 0.32 * sin(turned.y * 0.019 - around * 3.0 + 1.7)
        + 0.13 * sin((turned.x + turned.y) * 0.033 + 2.9)));

    return deck;
}

/** The cloud field, turned about the storm that has hold of this position. */
vec2 vortexdread_deck_swirl(vec2 cloud_xz, VortexdreadDeck deck) {
    // A column sample was turned by the warp already, and by a great deal more than this.
    if (deck.spin == 0.0 || vortexdread_column > 0.0) {
        return cloud_xz;
    }
    vec2 from_axis = cloud_xz - vortexdread_axis;
    float c = cos(deck.spin);
    float s = sin(deck.spin);
    return vortexdread_axis
        + vec2(from_axis.x * c - from_axis.y * s, from_axis.x * s + from_axis.y * c);
}

/**
 * How much of this sample the storm has hold of, at this height.
 *
 * <p>The radius comes from the height rather than from the plan: narrow where the inflow comes in,
 * widest where the updraft runs out of buoyancy, and soft at the rim. A radius that does not change
 * with height can only ever draw the side of a cylinder.
 */
float vortexdread_deck_tower(VortexdreadDeck deck, float altitude_fraction) {
    float spread = mix(vortexdread_foot_reach, vortexdread_anvil_reach,
                       smoothstep(0.0, vortexdread_crest, altitude_fraction));
    float cap = 1.0 - smoothstep(vortexdread_crest, vortexdread_ceil_fade, altitude_fraction);
    return (1.0 - smoothstep(spread * 0.30, spread * 1.35, deck.edge)) * deck.meso * cap;
}

/** The lowering, which only exists in the bottom of the layer. */
float vortexdread_deck_lowering(VortexdreadDeck deck, float altitude_fraction) {
    return deck.wall * (1.0 - smoothstep(0.0, vortexdread_wall_deep, altitude_fraction));
}

/**
 * The pack's own taper toward the floor of the layer, as the storm leaves it.
 *
 * <p>Applied after everything else in the density function, so a base built earlier is thrown away by
 * it unless it is let through here. Fair weather cumulus really do thin out underneath; the one cloud
 * that does not is the storm, whose base is flat, solid and low enough to touch.
 *
 * <p>The column gets its own profile here instead, which is where the shape belongs: this is the last
 * thing multiplying the density, so an edge written here fades the sheath out cleanly, where the same
 * edge written before the erosion would be chewed into a fringe by it and then pushed back to one by the
 * sharpening. What the column must not do is keep the pack's number, since that number is the taper of
 * the bottom of a layer it is two hundred metres below.
 */
float vortexdread_deck_base_taper(float taper, VortexdreadDeck deck, float altitude_fraction) {
    if (vortexdread_column > 0.0) {
        return vortexdread_column;
    }
    float hold = max(vortexdread_deck_tower(deck, altitude_fraction),
                     vortexdread_deck_lowering(deck, altitude_fraction));
    return mix(taper, vortexdread_base_hold, hold);
}

/**
 * The density the pack computed, as the storm leaves it.
 *
 * <p>Nothing here is a shape drawn on top. Coverage is added to the field that was already there, so
 * the gaps close and the lumps join while what was brighter stays brighter and the detail passes below
 * still have room to carve. The radius is taken from the height, so the mass rises as a tower and
 * spreads as an anvil rather than standing as a cylinder. The base is let down to the floor of the
 * layer under the funnel while the deck beside it still starts a fifth of the way up. Outside all of it
 * the layer is thinned, which is the one of the four that still works once the sky the storm brought
 * with it has gone to full coverage on its own.
 *
 * <p>And the column closes the gaps of the deck it read from part of the way rather than filling them,
 * because condensation in a vortex is continuous where a cloud is not. Part of the way is the whole
 * subtlety: its lumps stay the deck's lumps, which is the point of reading it from up there, and the two
 * detail passes below still find headroom to carve. Its shape is not applied here at all, it waits for
 * the taper at the end of the density function.
 */
float vortexdread_deck_shape(float density, float shaped, float altitude_fraction,
                             VortexdreadDeck deck) {
    vortexdread_deck_hold = 0.0;
    vortexdread_deck_depth = 0.0;
    vortexdread_deck_reach =
        (1.0 - smoothstep(1.0, vortexdread_gloom_span, deck.edge)) * vortexdread_descent;

    // The thinning applies wherever the ring falls, storm or no storm. Applying it only outside would
    // put a step in the density at the radius where the two branches meet, and a step in a cloud field
    // is a straight line in the sky.
    float thinned = shaped * (1.0 - vortexdread_slot * deck.slot);

    if (vortexdread_column > 0.0) {
        // Closed toward full rather than raised by a constant. A constant takes every sample already
        // above its own ceiling to the same number, and a field with one number in it is a surface with
        // no grain on it: the lumps the column was read from are the whole reason for reading it up
        // there instead of drawing a cone.
        float knitted = density + vortexdread_column_knit * (1.0 - density);
        return min(knitted, vortexdread_column_cap);
    }
    if (deck.meso < 0.002 && deck.wall < 0.002) {
        return thinned;
    }

    float tower = vortexdread_deck_tower(deck, altitude_fraction);
    float lowering = vortexdread_deck_lowering(deck, altitude_fraction);

    // Coverage, not opacity. What is added here still has to pass the pack's two detail passes, and
    // those carve in proportion to the headroom left above the sample, so the ceiling is what keeps the
    // mass a cloud instead of a solid.
    float organised = min(density + vortexdread_fill * tower + vortexdread_wall_fill * lowering,
                          vortexdread_ceiling);

    // Where the cloud is allowed to start. Under the storm it reaches the floor of the layer while the
    // deck beside it still begins a fifth of the way up, and that step is the whole of what a lowering
    // looks like from underneath.
    float base = mix(0.2, vortexdread_meso_hang, tower);
    base = mix(base, vortexdread_hang, lowering);
    base = max(base + deck.lip * max(tower, lowering), 0.0);
    float foot = smoothstep(base - vortexdread_underside,
                            base + vortexdread_underside, altitude_fraction);

    float hold = max(tower, lowering);
    vortexdread_deck_hold = hold;
    vortexdread_deck_depth = 1.0 - smoothstep(0.0, vortexdread_ceil_fade, altitude_fraction);
    return mix(thinned, max(thinned, organised * foot), hold);
}

#endif // INCLUDE_VORTEXDREAD_DECK
