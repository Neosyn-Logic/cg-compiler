/**
 * Title      : Generated from com.neosyn.test.golden.CondReadReduction by Neosyn IDE
 * File       : com.neosyn.test.golden.CondReadReduction.v
 * Standard   : Verilog-2001
 */

/*
 * Copyright (c) 2024-2026 Neosyn. All rights reserved.
 *
 * Golden file test - a streaming reduction that reads a per-group side input
 * (bias) CONDITIONALLY. Guards the no-FSM ready-assignment fix: a conditional
 * stream read must assert the port's `ready` (and clear its skid buffer) ONLY
 * in the actions that actually read it. Before the fix, the no-FSM path
 * asserted `bias_ready` in every action, so the cnt==1 actions advanced the
 * producer and dropped the buffered value — the next group read a STALE bias.
 * The golden Verilog pins the correct per-branch ready structure.
 */

/**
 * Grouped reduction (group length 2): acc = bias + a0 + a1, emitted once per
 * group. `a` is read every cycle; `bias` is read only at the start of a group.
 */
module CondReadReduction(
  input clock,
  input reset_n,
  input signed [31 : 0] a,
  input a_valid,
  output reg a_ready,
  input signed [31 : 0] bias,
  input bias_valid,
  output reg bias_ready,
  output reg signed [31 : 0] y,
  input y_ready,
  output reg y_valid
);


  /**
   * State variables
   */
  reg signed [31 : 0] cnt;
  reg signed [31 : 0] acc;
  reg  stall;
  reg  internal_a_valid;
  reg signed [31 : 0] internal_a;
  reg  internal_bias_valid;
  reg signed [31 : 0] internal_bias;
  
  
  
  
  /**
   * Combinational process
   */
  always @(*) begin
    a_ready = 1'b0;
    bias_ready = 1'b0;
    if ((((internal_bias_valid || bias_valid) && (internal_a_valid || a_valid)) && ((cnt == 32'sh0) && (cnt == 32'sh1)))) begin // line 27
      bias_ready = !stall;
      a_ready = !stall;
    end else if ((((internal_bias_valid || bias_valid) && (internal_a_valid || a_valid)) && ((cnt == 32'sh0) && !(cnt == 32'sh1)))) begin // line 27
      bias_ready = !stall;
      a_ready = !stall;
    end else if (((internal_a_valid || a_valid) && (!(cnt == 32'sh0) && (cnt == 32'sh1)))) begin // line 28
      a_ready = !stall;
    end else if (((internal_a_valid || a_valid) && (!(cnt == 32'sh0) && !(cnt == 32'sh1)))) begin // line 28
      a_ready = !stall;
    end else begin
      bias_ready = (!stall && !(internal_bias_valid || bias_valid));
      a_ready = (!stall && !(internal_a_valid || a_valid));
    end
  end
  
  /**
   * Synchronous process
   */
  always @(negedge reset_n or posedge clock) begin // body of CondReadReduction
    if (~reset_n) begin
      cnt <= 32'sh0;
      acc <= 32'sh0;
      stall <= 1'b0;
      internal_a_valid <= 1'b0;
      internal_a <= 32'b0;
      internal_bias_valid <= 1'b0;
      internal_bias <= 32'b0;
      y <= 32'b0;
      y_valid <= 1'b0;
    end else begin
      if (a_valid) begin
        internal_a_valid <= 1'b1;
        internal_a <= a;
      end
      
      if (bias_valid) begin
        internal_bias_valid <= 1'b1;
        internal_bias <= bias;
      end
      
      
      // Each stream output stays presented until THAT output is taken (valid && ready).
      y_valid <= y_valid && !y_ready;
      if (stall && (!y_valid || y_ready)) begin
        stall <= 1'b0;
      end
      // The scheduler runs every cycle. An action that writes a back-pressured stream output
      // additionally requires that output's slot to be free -- see its own guard below.
      if ((((internal_bias_valid || bias_valid) && (internal_a_valid || a_valid)) && ((cnt == 32'sh0) && (cnt == 32'sh1))) && (!y_valid || y_ready)) begin // line 27
        y <= ((internal_bias_valid ? internal_bias : bias) + (internal_a_valid ? internal_a : a));
        y_valid <= 1'b1;
        cnt <= 32'sh0;
        acc <= 32'sh0;
        stall <= !y_ready;
        internal_a_valid <= 1'b0;
        internal_bias_valid <= 1'b0;
      end else if ((((internal_bias_valid || bias_valid) && (internal_a_valid || a_valid)) && ((cnt == 32'sh0) && !(cnt == 32'sh1)))) begin // line 27
        acc <= ((internal_bias_valid ? internal_bias : bias) + (internal_a_valid ? internal_a : a));
        cnt <= (cnt + 32'sh1);
        internal_a_valid <= 1'b0;
        internal_bias_valid <= 1'b0;
      end else if (((internal_a_valid || a_valid) && (!(cnt == 32'sh0) && (cnt == 32'sh1))) && (!y_valid || y_ready)) begin // line 28
        y <= (acc + (internal_a_valid ? internal_a : a));
        y_valid <= 1'b1;
        cnt <= 32'sh0;
        acc <= 32'sh0;
        stall <= !y_ready;
        internal_a_valid <= 1'b0;
      end else if (((internal_a_valid || a_valid) && (!(cnt == 32'sh0) && !(cnt == 32'sh1)))) begin // line 28
        acc <= (acc + (internal_a_valid ? internal_a : a));
        cnt <= (cnt + 32'sh1);
        internal_a_valid <= 1'b0;
      end
    end
  end

endmodule //CondReadReduction
