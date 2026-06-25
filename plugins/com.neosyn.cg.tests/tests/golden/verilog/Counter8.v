/*
 * Copyright (c) 2024 Neosyn. All rights reserved.
 *  *
 * Golden file test - Basic tasks for regression testing.
 * Tests: simple state variables, arithmetic, output ports.
 */

/**
 * Simple 8-bit counter with wrap-around.
 * Tests: increment, overflow, output port.
 */
module Counter8(input clock, input reset_n, output reg [7 : 0] count);


  /**
   * State variables
   */
  reg [7 : 0] value;
  
  
  
  
  
  /**
   * Synchronous process
   */
  always @(negedge reset_n or posedge clock) begin // body of Counter8
    if (~reset_n) begin
      value <= 8'h0;
      count <= 8'b0;
    end else begin
      
      begin : FSM_Counter8_a // line 34
        reg [8 : 0] orion;
      
        count <= value;
        orion = (value + 8'h1);
        value <= orion[7 : 0];
      end
    end
  end

endmodule //Counter8
