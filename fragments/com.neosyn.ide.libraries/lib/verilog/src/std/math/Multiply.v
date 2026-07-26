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
 * Title   : Signed integer multiplier (std.math.Multiply built-in)
 * Authors : Neosyn team <nicolas.siret@neosyn.io>
 *
 * p = a * b, signed, full width (p is 2*width = pwidth bits, so no product bit is
 * lost). The product is REGISTERED (1-cycle latency), which breaks the long
 * combinational path of a bare `*` so the tool can place/retime it into the DSP's
 * pipeline registers.
 *
 * `sync ready` handshake (identical to std.math.Divide): a_ready/b_ready fall
 * while a product is in flight, so a producer is naturally back-pressured; p is
 * held until p_ready. Each operand is latched into a holding slot as it arrives
 * (a and b may be presented on different cycles) and the product is captured once
 * both are held.
 */
module Multiply
  #(parameter width  = 32,
    parameter pwidth = 64)
  (
    input clock,
    input reset_n,
    input  [width - 1 : 0] a, input a_valid, output a_ready,
    input  [width - 1 : 0] b, input b_valid, output b_ready,
    output reg [pwidth - 1 : 0] p, output reg p_valid, input p_ready
  );

  localparam S_IDLE = 1'b0, S_DONE = 1'b1;

  reg              state;
  reg [width-1:0]  ra, rb;
  reg              have_a, have_b;

  assign a_ready = (state == S_IDLE) & ~have_a;
  assign b_ready = (state == S_IDLE) & ~have_b;

  // Full-width signed product. Both operands are sign-extended to pwidth by the
  // signed context, so the low pwidth bits are the exact 2*width-bit product.
  wire signed [pwidth-1:0] prod = $signed(ra) * $signed(rb);

  always @(negedge reset_n or posedge clock) begin
    if (~reset_n) begin
      p <= {pwidth{1'b0}}; p_valid <= 1'b0;
      have_a <= 1'b0; have_b <= 1'b0; state <= S_IDLE;
    end else begin
      case (state)
        S_IDLE: begin
          if (a_valid & a_ready) begin ra <= a; have_a <= 1'b1; end
          if (b_valid & b_ready) begin rb <= b; have_b <= 1'b1; end
          if (have_a & have_b) begin
            have_a <= 1'b0; have_b <= 1'b0;
            p       <= prod;         // registered product (1-cycle latency)
            p_valid <= 1'b1;
            state   <= S_DONE;
          end
        end
        S_DONE: if (p_ready) begin p_valid <= 1'b0; state <= S_IDLE; end
        default: state <= S_IDLE;
      endcase
    end
  end

endmodule
