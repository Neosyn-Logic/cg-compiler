/*
 * Copyright (c) 2012-2021, Neosyn
 * All rights reserved.
 * 
 * REDISTRIBUTION of this file in source and binary forms, with or without
 * modification, is NOT permitted in any way.
 *
 * The use of this file in source and binary forms, with or without
 * modification, is permitted if you have a valid commercial license of
 * Neosyn IDE.
 * If you do NOT have a valid license of Neosyn IDE: you are NOT allowed
 * to use this file.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE
 */
/**
 * Title   : Signed integer divider (std.math.Divide built-in)
 * Authors : Neosyn team <nicolas.siret@neosyn.io>
 *
 * q = a / b, signed, truncated toward zero (b == 0 is undefined, as for `/`).
 * `sync ready` handshake: a_ready/b_ready fall while the divider computes, so a
 * producer is naturally back-pressured; q is held until q_ready.
 *
 * use_hard = 0 (default): portable sequential shift/subtract divider. One
 *   subtractor + a shift register + a small FSM — synthesizes on ANY FPGA with a
 *   short critical path (~width cycles/quotient). Operates on operand MAGNITUDES
 *   for exact truncation-toward-zero, re-applying the sign at the end. A
 *   power-of-two divisor is handled combinationally as an arithmetic shift, so
 *   /2, /4, /8, ... complete in a single cycle.
 * use_hard = 1: single-cycle native $signed(a)/$signed(b) — only sound on parts
 *   with a hardware divider/DSP block; elsewhere the combinational `/` bloats
 *   logic and caps Fmax.
 */
module Divide
  #(parameter width    = 32,
    parameter use_hard = 0)
  (
    input clock,
    input reset_n,
    input  [width - 1 : 0] a, input a_valid, output a_ready,
    input  [width - 1 : 0] b, input b_valid, output b_ready,
    output reg [width - 1 : 0] q, output reg q_valid, input q_ready
  );

  localparam S_IDLE = 2'd0, S_CALC = 2'd1, S_DONE = 2'd2;

  // Operand capture is common to both engines: each operand is latched into a
  // holding slot as it arrives (a `sync ready` producer may present a and b on
  // different cycles), and the divide starts once both are held.
  reg [1:0]         state;
  reg [width-1:0]   ra, rb;
  reg               have_a, have_b;

  assign a_ready = (state == S_IDLE) & ~have_a;
  assign b_ready = (state == S_IDLE) & ~have_b;

  generate
  if (use_hard != 0) begin : g_hard
    // -------- Hard-divider / DSP path: single-cycle native division. --------
    // Compute the quotient in a standalone SIGNED wire: folding it into a
    // ternary whose other arm is an unsigned zero would make the whole
    // expression unsigned and silently turn `/` into an unsigned divide.
    wire signed [width-1:0] hquot = $signed(ra) / $signed(rb);
    always @(negedge reset_n or posedge clock) begin
      if (~reset_n) begin
        q <= {width{1'b0}}; q_valid <= 1'b0;
        have_a <= 1'b0; have_b <= 1'b0; state <= S_IDLE;
      end else begin
        case (state)
          S_IDLE: begin
            if (a_valid & a_ready) begin ra <= a; have_a <= 1'b1; end
            if (b_valid & b_ready) begin rb <= b; have_b <= 1'b1; end
            if (have_a & have_b) begin
              have_a <= 1'b0; have_b <= 1'b0;
              q       <= (rb == {width{1'b0}}) ? {width{1'b0}} : hquot;
              q_valid <= 1'b1;
              state   <= S_DONE;
            end
          end
          S_DONE: if (q_ready) begin q_valid <= 1'b0; state <= S_IDLE; end
          default: state <= S_IDLE;
        endcase
      end
    end
  end else begin : g_seq
    // -------- Portable sequential shift/subtract divider (magnitudes). -------
    localparam ITER = width + 1;                 // |INT_MIN| needs one extra bit
    localparam CW   = $clog2(width + 2);

    reg  [width:0]   dvnd;                        // dividend magnitude (MSB-first)
    reg  [width:0]   dsor;                        // divisor magnitude
    reg  [width+1:0] rem;                         // running remainder (+headroom)
    reg  [width:0]   quo;                         // quotient magnitude
    reg              sgn;                         // sign of the quotient
    reg  [CW-1:0]    cnt;

    // Magnitudes of the captured operands (sign-extended by 1 bit so that the
    // magnitude of the most-negative value is representable).
    wire [width:0] amag = ra[width-1] ? (~{ra[width-1], ra} + 1'b1) : {1'b0, ra};
    wire [width:0] bmag = rb[width-1] ? (~{rb[width-1], rb} + 1'b1) : {1'b0, rb};
    wire           b_zero = (rb == {width{1'b0}});
    wire           b_pow2 = ~b_zero & ((bmag & (bmag - 1'b1)) == {(width+1){1'b0}});

    // Shift amount for a power-of-two divisor = index of its single set bit.
    integer i;
    reg [CW-1:0] sh;
    always @(*) begin
      sh = {CW{1'b0}};
      for (i = 0; i <= width; i = i + 1)
        if (bmag[i]) sh = i[CW-1:0];
    end
    wire [width:0] qmag_fast = amag >> sh;        // exact: magnitude shift trunc-to-0
    wire           sgn_new   = ra[width-1] ^ rb[width-1];

    // One restoring shift/subtract step.
    wire [width+1:0] rem_sh  = {rem[width:0], dvnd[width]};
    wire [width+1:0] rem_sub = rem_sh - {1'b0, dsor};
    wire             ge      = (rem_sh >= {1'b0, dsor});
    wire [width:0]   qmag_nx = {quo[width-1:0], ge};  // magnitude after this step

    always @(negedge reset_n or posedge clock) begin
      if (~reset_n) begin
        q <= {width{1'b0}}; q_valid <= 1'b0;
        have_a <= 1'b0; have_b <= 1'b0; state <= S_IDLE;
      end else begin
        case (state)
          S_IDLE: begin
            if (a_valid & a_ready) begin ra <= a; have_a <= 1'b1; end
            if (b_valid & b_ready) begin rb <= b; have_b <= 1'b1; end
            if (have_a & have_b) begin
              have_a <= 1'b0; have_b <= 1'b0;
              sgn <= sgn_new;
              if (b_zero) begin
                q <= {width{1'b0}}; q_valid <= 1'b1; state <= S_DONE;  // undefined
              end else if (b_pow2) begin
                q <= sgn_new ? (~qmag_fast + 1'b1) : qmag_fast[width-1:0];
                q_valid <= 1'b1; state <= S_DONE;                      // 1-cycle shift
              end else begin
                dvnd <= amag; dsor <= bmag;
                rem <= {(width+2){1'b0}}; quo <= {(width+1){1'b0}};
                cnt <= ITER[CW-1:0]; state <= S_CALC;
              end
            end
          end
          S_CALC: begin
            rem  <= ge ? rem_sub : rem_sh;
            dvnd <= dvnd << 1;
            quo  <= qmag_nx;
            cnt  <= cnt - 1'b1;
            if (cnt == {{(CW-1){1'b0}}, 1'b1}) begin      // last step
              q       <= sgn ? (~qmag_nx + 1'b1) : qmag_nx[width-1:0];
              q_valid <= 1'b1;
              state   <= S_DONE;
            end
          end
          S_DONE: if (q_ready) begin q_valid <= 1'b0; state <= S_IDLE; end
          default: state <= S_IDLE;
        endcase
      end
    end
  end
  endgenerate

endmodule
