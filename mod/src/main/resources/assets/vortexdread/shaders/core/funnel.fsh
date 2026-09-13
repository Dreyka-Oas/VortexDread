#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>

in vec3 rayTarget;
in vec4 funnelTint;
in vec4 lightMapColor;

flat in vec2 axisXZ;
flat in float groundY;
flat in float coreRadius;
flat in float funnelHeight;
flat in float windFraction;
flat in float descent;
flat in float groundLoad;

out vec4 fragColor;

// The same texture a patched shader pack reads the storms out of. A core shader gets no uniform of
// its own, so the three render settings ride in two of its texels, written on every row.
uniform sampler2D Sampler0;

// How much wider the condensation is where it meets the wall cloud than where it meets the ground. The
// wind field flares three times as hard, and it is right to: the damaging inflow really does cover that
// much ground under the base. What condenses is a much narrower thing inside it, and drawing the wind's
// own shape puts a trumpet in the sky where a photograph has a cone.
const float WAIST = 0.58;
const float FLARE = 0.78;

// Where the render settings sit in that texture, and the ranges they were folded into.
const int SETTINGS_FIELD = 6;
const float STEPS_SPAN = 256.0;
const float DETAIL_SPAN = 32.0;

// The wisp size the noise frequencies below were tuned against, in blocks. Matches LookConfig's own
// default, so a player who never touches the setting gets exactly what was tuned.
const float DETAIL_REFERENCE = 1.8;

/** The two bytes the client wrote for one number, back into the number. */
float unpack16(float low, float high) {
    return (low * 255.0 + high * 255.0 * 256.0) * (1.0 / 65535.0);
}

// Steps along the view ray, then toward the light for the self shadowing that gives the column its
// volume. Enough of the first to hide the banding on a funnel that fills the screen, few enough that a
// machine without a shaderpack still holds its frame budget.
int marchSteps() {
    vec4 quality = texelFetch(Sampler0, ivec2(SETTINGS_FIELD, 0), 0);
    return int(max(8.0, unpack16(quality.x, quality.y) * STEPS_SPAN));
}

int lightMarchSteps() {
    vec4 quality = texelFetch(Sampler0, ivec2(SETTINGS_FIELD, 0), 0);
    return int(unpack16(quality.z, quality.w) * STEPS_SPAN);
}

/** Size of the smallest wisp worth drawing, in blocks. */
float detailSize() {
    vec4 quality = texelFetch(Sampler0, ivec2(SETTINGS_FIELD + 1, 0), 0);
    return max(0.2, unpack16(quality.x, quality.y) * DETAIL_SPAN);
}

// Below this a sample is not worth a light march: the shadow it would receive changes nothing anyone
// can see, and the march is the most expensive thing in the program.
const float LIT_THRESHOLD = 0.04;

// Extinction per unit of density. Higher makes a solid wall, lower a wisp. A mature funnel is opaque:
// nothing behind it shows through, which is why the ones in photographs read as black against the rain
// rather than as a grey smudge over it.
const float EXTINCTION = 0.62;

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

float valueNoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float n000 = hash13(i);
    float n100 = hash13(i + vec3(1.0, 0.0, 0.0));
    float n010 = hash13(i + vec3(0.0, 1.0, 0.0));
    float n110 = hash13(i + vec3(1.0, 1.0, 0.0));
    float n001 = hash13(i + vec3(0.0, 0.0, 1.0));
    float n101 = hash13(i + vec3(1.0, 0.0, 1.0));
    float n011 = hash13(i + vec3(0.0, 1.0, 1.0));
    float n111 = hash13(i + vec3(1.0, 1.0, 1.0));
    return mix(
        mix(mix(n000, n100, f.x), mix(n010, n110, f.x), f.y),
        mix(mix(n001, n101, f.x), mix(n011, n111, f.x), f.y),
        f.z);
}

float fbm(vec3 p) {
    float total = 0.0;
    float amplitude = 0.5;
    for (int i = 0; i < 3; i++) {
        total += amplitude * valueNoise(p);
        p *= 2.03;
        amplitude *= 0.5;
    }
    return total;
}

/** Radius of the condensation wall at a height fraction, before the turbulence roughens it. */
float wallRadius(float h) {
    // Narrow where it meets the ground and widest against the cloud, which is the way round a funnel
    // actually stands. A tube of even width reads as a chimney, and one that opens downward reads as a
    // party hat: what a photograph has is a wall that stands nearly vertical through the middle of its
    // height and opens out over the last third.
    float profile = (WAIST + 0.16 * windFraction) + (FLARE + 0.25 * windFraction) * h * h;

    // And it spreads at the very bottom, in the metre or two where the surface stops the inflow from
    // going any further down and it has to turn.
    profile += 0.22 * (1.0 - smoothstep(0.0, 0.09, h));
    return coreRadius * profile;
}

// How far the lofted ground reaches with an empty funnel and with a full one, as multiples of the core
// radius. Kept in step with TornadoRenderer, which sizes the box from the same two numbers. Measured off
// photographs rather than guessed: the skirt of a violent one is a little under three times the width of
// the condensation above it, and anything past that stops reading as a tornado and starts reading as fog.
const float RING_REACH = 1.25;
const float RING_SWELL = 0.9;

// Slack left round that radius for the lobes to push into, and how far below the funnel's own ground the
// dust is allowed to run. Both are matched in the vertex program and in TornadoRenderer.
const float RING_ROUGH = 1.3;
const float RING_DROP = 0.9;

/** How far out the debris ring reaches at the foot of the column. */
float ringRadius() {
    return coreRadius * (RING_REACH + RING_SWELL * groundLoad);
}

/**
 * Density of the ground the storm has in the air, at a point.
 *
 * <p>This is the part of a tornado that does the damage and the part a photograph shows. It is not a
 * cone: air runs inward along the surface, arrives at the core with nowhere left to go, and what it
 * picked up on the way goes up in a ring around the base. So it is tallest against the core wall, it
 * thins outward to a ragged edge that rolls along the ground, and it is opaque where the condensation
 * above it is a sheath.
 *
 * <p>Its whole size follows what the sweep is actually tearing out. A funnel crossing bare stone keeps
 * its condensation and loses its ring, which is the difference between a tornado over a field and the
 * same tornado over water.
 */
float ringDensity(vec3 p, float roughness) {
    if (groundLoad < 0.004) {
        return 0.0;
    }
    float drop = coreRadius * RING_DROP;
    float above = p.y - groundY;
    if (above < -drop) {
        return 0.0;
    }

    vec2 offset = p.xz - axisXZ;
    float radius = length(offset);

    // The edge of a debris cloud is a ring of lobes rolling outward and round, never a circle. One more
    // noise lookup buys that, taken on a coordinate that closes on itself so the ring has no seam at the
    // back. A second full band of turbulence would cost twice as much and show half as much.
    float spin = atan(offset.y, offset.x) + GameTime * 2400.0 * 0.22;
    float lobes = valueNoise(vec3(cos(spin), sin(spin), above * 0.04) * 3.2) - 0.5;
    float outer = ringRadius() * (1.0 + 0.42 * lobes + 0.2 * roughness);
    if (radius > outer) {
        return 0.0;
    }

    // Rising against the core, spreading and settling further out. A ring that only reaches the height
    // of its own core radius reads as a puff at the feet, and one that climbs as high as it is wide
    // swallows the funnel it is supposed to be standing under.
    float lid = coreRadius * (0.35 + 0.6 * groundLoad)
              * (1.0 - 0.72 * smoothstep(0.1, 1.0, radius / outer));
    // The column's own grain only leans on the stack here rather than setting it. Driving both ends of
    // the fade from one smooth field draws its level sets across the whole dust plane, which reads as
    // contour lines on a map and not as anything a storm does.
    float stack = 1.0 - smoothstep(lid * (0.35 + 0.18 * roughness), lid * (1.25 + 0.35 * roughness), above);
    float edge = smoothstep(outer, outer * 0.62, radius);

    // Under the funnel's own ground the dust keeps going and thins: what is behind terrain is hidden by
    // the depth buffer anyway, and what is over a drop rolls down it the way it does in a photograph.
    float under = above < 0.0 ? 1.0 - smoothstep(0.0, drop, -above) : 1.0;

    return stack * edge * under * (0.5 + 1.9 * groundLoad) * descent;
}

// How far the lowered base reaches from the axis, as a multiple of the core radius, how deep it hangs
// under the deck as a fraction of the funnel's height, and how far it climbs back above that deck as a
// fraction of its own depth. All three are matched in TornadoRenderer, which sizes the prism to hold
// them. The climb is what lets the mass thin out at the top instead of ending on the box's lid.
const float WALL_REACH = 2.3;
const float WALL_HANG = 0.13;
const float WALL_RISE = 1.4;

/**
 * Density of the wall cloud the funnel comes out of.
 *
 * <p>A tornado does not hang off a flat cloud base. The mesocyclone pulls a block of that base down
 * with it, several times wider than the funnel and lowest right against it, and the funnel is that
 * block continuing downward rather than a separate object underneath it. Without it the column ends
 * at the deck on a line, which is the giveaway no amount of work on the column itself can cover.
 */
float wallCloudDensity(vec3 p) {
    float hang = funnelHeight * WALL_HANG;
    float rise = hang * WALL_RISE;
    float below = (groundY + funnelHeight) - p.y;
    if (below < -rise || below > hang) {
        return 0.0;
    }

    vec2 offset = p.xz - axisXZ;
    float radius = length(offset);
    // Its own grain, at the scale of the thing rather than at the scale of the column's striations,
    // and turning with it. Borrowing the column's noise here gives a disc with fine stripes on it,
    // which is the shape nobody has ever photographed.
    float turn = GameTime * 2400.0 * 0.09;
    vec2 spun = mat2(cos(turn), -sin(turn), sin(turn), cos(turn)) * offset;
    float lumps = fbm(vec3(spun, p.y * 1.4) * (1.6 / max(coreRadius, 1.0))) - 0.5;

    // The edge is torn rather than drawn: a lowering that ends on a circle reads as a saucer parked
    // over the field, and no photograph of one has that shape.
    float reach = coreRadius * WALL_REACH * (1.0 + 1.6 * lumps);
    if (radius > reach) {
        return 0.0;
    }

    // Lowest against the funnel and rising outward: a base being drawn down, not a disc parked under
    // the cloud.
    float lip = hang * (1.0 - 0.55 * smoothstep(0.0, 1.0, radius / reach)) * (1.0 + 1.3 * lumps);
    float body = 1.0 - smoothstep(lip * 0.35, lip, below);

    // Nothing ends on a flat lid. Above the nominal deck the mass thins out over its own depth, which
    // is where the pack's clouds or the game's take the picture over.
    float cap = 1.0 - smoothstep(rise * 0.05, rise * 1.2, -below);
    // Ramped the whole way in rather than plateauing: a lowering that holds one density out to a fixed
    // fraction of its reach has a brim, and a brim on a cloud is a hat.
    float rim = smoothstep(reach, reach * 0.12, radius);

    // It is already turning before anything comes out of it, and it stays after the funnel has roped
    // out, so it does not follow the descent the way the condensation does.
    return body * cap * rim * (0.8 + 0.7 * lumps);
}

/**
 * Density of condensate at a point, in the same frame the vertices arrived in.
 *
 * <p>A funnel is not a cone of fog. It is a sheath: water condenses where the pressure drop is
 * steepest, which is at the core wall, and the middle of a mature one is comparatively clear. Under
 * that sits the debris cloud, wider than the funnel and opaque, which is the part that does the damage
 * and the part photographs show as a ragged skirt rather than a cone.
 */
float funnelDensity(vec3 p, out float dustShare) {
    dustShare = 0.0;
    float h = clamp((p.y - groundY) / funnelHeight, 0.0, 1.0);
    float radius = length(p.xz - axisXZ);

    // Most steps on most rays land in clear air. Answering those before the noise is sampled is what
    // keeps a funnel that fills the screen inside a frame budget.
    float wall = wallRadius(h);
    float lifted = (p.y - groundY) < coreRadius * 5.0 ? ringRadius() * RING_ROUGH : 0.0;
    float lowered = (p.y - groundY) > funnelHeight * (1.0 - WALL_HANG)
        ? coreRadius * WALL_REACH * 1.8
        : 0.0;
    float outer = max(max(wall * 1.5, lifted), lowered);
    if (radius > outer) {
        return 0.0;
    }

    // The whole column turns, and the noise turns with it, so the striations wind round the funnel
    // rather than crawling across a surface that happens to be rotating underneath them.
    float turn = GameTime * 2400.0 * (0.6 + 0.4 * windFraction) / max(wall, 1.0);
    float angle = atan(p.z - axisXZ.y, p.x - axisXZ.x) + turn;
    // Air climbing the wall turns many times on the way up, so a mark on the surface traces a helix.
    // Winding the sampling ring with height is that helix, and it keeps the noise continuous where
    // shearing the height by the bearing would leave a seam down one side. Narrow columns turn faster
    // for the same wind, which is why the pitch tightens as the wall closes in.
    float wound = angle + (p.y - groundY) * (0.16 + 0.5 / max(wall, 2.0));
    float fineness = DETAIL_REFERENCE / detailSize();
    vec3 sample = vec3(cos(wound), 0.0, sin(wound)) * (radius * 0.35 * fineness)
                + vec3(0.0, (p.y * 0.16 - GameTime * 900.0) * fineness, 0.0);
    float roughness = fbm(sample) - 0.5;

    // A violent tornado rarely turns as one column. Two to five smaller vortices orbit the axis inside
    // the parent circulation and turn faster than it does, and that braid is what tells a photograph of
    // an EF4 from a photograph of an EF1. They live low, where the inflow is fastest and the pressure
    // drop steepest, and they need a parent wide enough to hold more than one. The core radius is the
    // absolute measure of that width: the wind fraction is read against each storm's own peak, so a
    // mature EF1 reads the same there as a mature EF5 and would braid just as hard.
    float violence = smoothstep(14.0, 40.0, coreRadius);
    float suctionCount = floor(2.0 + 3.0 * violence);
    float suctionRoom = smoothstep(0.62, 0.16, h) * violence * smoothstep(0.42, 0.80, windFraction);
    // Leaning with height, because each one is a vortex in its own right riding the parent's updraught
    // and trailing behind where it started.
    float braid = angle * suctionCount - GameTime * 2400.0 * 2.3 + (p.y - groundY) * 0.06;
    float suction = (0.5 + 0.5 * cos(braid)) * suctionRoom;

    // The striations have to bite. A funnel photographed at any distance is visibly made of separate
    // ropes of condensate winding round each other, and a wall that is only gently perturbed reads as
    // a smooth grey pipe however well it is lit.
    float edge = wall * (1.0 + 0.62 * roughness * (1.4 - h) + 0.34 * suction);
    float sheath = smoothstep(edge * 1.05, edge * 0.6, radius) * (1.0 + 1.0 * suction);

    // Hollow only well up the column. Down where the inflow arrives it is full of what it has picked
    // up, and a funnel drawn as a tube all the way to the ground shows daylight through its middle.
    float hollow = mix(1.0, smoothstep(edge * 0.25, edge * 0.65, radius),
                       0.55 * smoothstep(0.32, 0.95, h));

    // The condensation has only come down as far as the funnel has descended; above that it merges
    // into the wall cloud, below it there is clear air with dust in it.
    float reach = smoothstep(1.0 - descent - 0.08, 1.0 - descent + 0.06, h);

    // The very top thins out instead of ending on a flat lid: the funnel does not stop, it becomes the
    // wall cloud, and a hard edge up there is the one thing that gives the whole illusion away. It has
    // to start thinning well below the deck, or the last stretch reads as a separate pale cone hanging
    // under the cloud instead of as the cloud coming down.
    float crown = 1.0 - 0.4 * smoothstep(0.82, 1.0, h);

    // Condensation stops where the ground is. Only the dust runs on below, and the two are separated
    // here rather than at the bounds, because the bounds now reach under the funnel for that dust.
    float column = sheath * hollow * reach * crown * smoothstep(-2.0, 0.5, p.y - groundY);
    float ring = ringDensity(p, roughness);
    float lowering = wallCloudDensity(p);

    float total = column + ring + lowering;
    dustShare = total > 0.0 ? ring / total : 0.0;
    return clamp(total, 0.0, 1.6);
}

/** How much light reaches a point from above, by marching toward the sky. */
float lightTransmittance(vec3 p, float stepSize) {
    float depth = 0.0;
    float ignored;
    int lightSteps = lightMarchSteps();
    for (int i = 1; i <= lightSteps; i++) {
        depth += funnelDensity(p + vec3(0.0, stepSize * float(i), 0.0), ignored);
    }
    return exp(-depth * stepSize * EXTINCTION * 1.6);
}

/** Entry and exit of the view ray through the cylinder the funnel lives in. */
bool boundsInterval(vec3 origin, vec3 dir, out float t0, out float t1) {
    float maxRadius = max(max(wallRadius(1.0) * 1.5, ringRadius() * RING_ROUGH),
                          coreRadius * WALL_REACH);
    float floorY = groundY - coreRadius * RING_DROP;
    vec2 oc = origin.xz - axisXZ;
    float a = dot(dir.xz, dir.xz);
    float b = 2.0 * dot(oc, dir.xz);
    float c = dot(oc, oc) - maxRadius * maxRadius;
    if (a < 1.0e-6) {
        if (c > 0.0) {
            return false;
        }
        t0 = 0.0;
        t1 = 1.0e6;
    } else {
        float disc = b * b - 4.0 * a * c;
        if (disc < 0.0) {
            return false;
        }
        float root = sqrt(disc);
        t0 = (-b - root) / (2.0 * a);
        t1 = (-b + root) / (2.0 * a);
    }

    float top = groundY + funnelHeight * (1.0 + WALL_HANG * WALL_RISE);
    if (abs(dir.y) > 1.0e-6) {
        float ta = (floorY - origin.y) / dir.y;
        float tb = (top - origin.y) / dir.y;
        t0 = max(t0, min(ta, tb));
        t1 = min(t1, max(ta, tb));
    } else if (origin.y < floorY || origin.y > top) {
        return false;
    }

    t0 = max(t0, 0.0);
    return t1 > t0;
}

void main() {
    vec3 dir = normalize(rayTarget);
    float t0;
    float t1;
    if (!boundsInterval(vec3(0.0), dir, t0, t1)) {
        discard;
    }
    t1 = min(t1, length(rayTarget));
    if (t1 <= t0) {
        discard;
    }

    int steps = marchSteps();
    float stepSize = (t1 - t0) / float(steps);
    // A fixed step pattern draws visible shells; offsetting the first sample by a per-pixel amount
    // turns that banding into noise the eye stops seeing.
    float jitter = hash13(vec3(gl_FragCoord.xy, GameTime * 40000.0));

    // Condensation is water, and water is white. What colours a funnel is the ground in it, and even a
    // storm eating a forest comes out grey rather than green: the ingested colour is pulled most of the
    // way back to its own brightness before anything is drawn with it.
    float grey = dot(funnelTint.rgb, vec3(0.299, 0.587, 0.114));
    vec3 body = mix(vec3(grey), funnelTint.rgb, 0.3);
    // Darker than the sky it stands against, always. A column this thick lets almost nothing through,
    // so what reaches the eye is the little that scattered off its near face, and a funnel lit to the
    // brightness of the overcast behind it is the single thing that reads as a game rather than as
    // weather. The floor is what keeps the shaded side off pure black.
    vec3 skyLit = body * (0.20 + 0.62 * lightMapColor.rgb);
    vec3 flashLit = mix(skyLit, vec3(1.0, 0.96, 0.82), funnelTint.a * 0.85);

    // The ring is ground, not water, so it keeps the colour it was torn out of and it keeps it darker.
    // A funnel eating a field and a funnel eating a quarry are the same shape and nothing like the same
    // photograph, and this is the line that decides which one a player is looking at.
    vec3 dustLit = mix(vec3(grey), funnelTint.rgb, 0.85) * (0.26 + 0.60 * lightMapColor.rgb);
    vec3 dustFlashLit = mix(dustLit, vec3(0.95, 0.88, 0.72), funnelTint.a * 0.7);

    vec3 scattered = vec3(0.0);
    float transmittance = 1.0;
    float depthSum = 0.0;
    float depthWeight = 0.0;

    for (int i = 0; i < steps; i++) {
        if (transmittance < 0.02) {
            break;
        }
        float t = t0 + (float(i) + jitter) * stepSize;
        vec3 p = dir * t;
        float dustShare;
        float density = funnelDensity(p, dustShare);
        if (density <= 0.001) {
            continue;
        }
        float sigma = density * EXTINCTION * stepSize;
        // A second march per sample is the most expensive thing in this loop, and it is paid on every
        // pixel of a funnel that fills the screen. Past this depth the sample is scaled by so little
        // transmittance that whatever shading the march would return cannot be told from a constant.
        float lit = 1.0;
        if (density > LIT_THRESHOLD) {
            lit = transmittance > 0.15 ? lightTransmittance(p, funnelHeight / 48.0) : 0.45;
        }

        // The column is shadowed by itself, which is what gives it its volume. The dust at the foot is
        // not: it sits in open light on every side, and shading it as if the funnel were standing on it
        // paints the ring as the column's shadow rather than as ground in the air.
        float h = clamp((p.y - groundY) / funnelHeight, 0.0, 1.0);
        vec3 material = mix(flashLit, dustFlashLit, dustShare);
        float shade = mix(0.12 + 0.88 * lit, 0.3 + 0.4 * lit, dustShare);

        // Dark at the foot and lighter against the cloud, which is the gradient every
        // photograph of one has: the bottom is buried in its own rain and its own dust.
        float height = mix(0.3 + 0.5 * h, 0.5, dustShare);
        vec3 colour = material * shade * height;

        float absorbed = 1.0 - exp(-sigma);
        float contribution = absorbed * transmittance;
        scattered += colour * contribution;
        depthSum += t * contribution;
        depthWeight += contribution;
        transmittance *= exp(-sigma);
    }

    float alpha = 1.0 - transmittance;
    if (alpha < 0.004) {
        discard;
    }

    // Fogged at the depth the condensation is actually at. Taking it from the vertex would fog the
    // column as if it stood on the far wall of its own bounding box, which is a render distance
    // further out, and a funnel painted entirely in sky colour is a funnel nobody can see.
    vec3 seen = dir * (depthWeight > 0.0 ? depthSum / depthWeight : t0);
    vec4 result = vec4(scattered / max(alpha, 1.0e-4), alpha) * ColorModulator;
    fragColor = apply_fog(result, fog_spherical_distance(seen), fog_cylindrical_distance(seen),
            FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
