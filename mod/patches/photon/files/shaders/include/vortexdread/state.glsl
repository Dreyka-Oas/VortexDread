#if !defined INCLUDE_VORTEXDREAD_STATE
#define INCLUDE_VORTEXDREAD_STATE

/*
  The one sampler the storms arrive through.

  A mod cannot hand a shader pack a uniform, so the state rides in as a texture the mod rewrites every
  frame: one row per funnel, one texel per field, sixteen bits for anything that has to be accurate.

  It asks for a sampler of its own rather than borrowing a colortex. Borrowing one costs the pack
  whatever it kept there, and on this pack colortex15 is the combined depth buffer it builds when a
  terrain mod is loaded, so a player running Distant Horizons or Voxy would have been trading the far
  horizon for the funnel. The name below is settled in shaders.properties and the two have to agree.
*/
uniform sampler2D vortexdread_state;

#endif // INCLUDE_VORTEXDREAD_STATE
