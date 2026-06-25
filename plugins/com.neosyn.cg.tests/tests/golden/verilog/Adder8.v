/*
 * Copyright (c) 2024 Neosyn. All rights reserved.
 *  *
 * Golden file test - Basic tasks for regression testing.
 * Tests: simple state variables, arithmetic, output ports.
 */

/**
 * Simple adder - two inputs, one output.
 * Tests: multiple input ports, arithmetic.
 */
module Adder8(input clock, input reset_n, input [7 : 0] a, input [7 : 0] b, output reg [7 : 0] sum);


  /**
   * State variables
   */
  
  
  
  
  
  /**
   * Synchronous process
   */
  always @(negedge reset_n or posedge clock) begin // body of Adder8
    if (~reset_n) begin
      sum <= 8'b0;
    end else begin
      
      begin : FSM_Adder8_a // line 70
        reg [8 : 0] orion;
      
        orion = (a + b);
        sum <= orion[7 : 0];
      end
    end
  end

endmodule //Adder8
