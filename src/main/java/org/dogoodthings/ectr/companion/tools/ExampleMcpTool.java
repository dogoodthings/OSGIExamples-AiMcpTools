//*****************************************************************************
//
//                            Copyright (c) 2026
//                    DSC Software AG, Karlsruhe, Germany
//                            All rights reserved
//
//        The contents of this file is an unpublished work protected
//        under the copyright law of the Federal Republic of Germany
//
//        This software is proprietary to and embodies confidential
//        technology of DSC Software AG. Possession, use and copying
//        of the software and media is authorized only pursuant to a
//        valid written license from DSC Software AG. This copyright
//        statement must be visibly included in all copies.
//
//*****************************************************************************

package org.dogoodthings.ectr.companion.tools;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import com.dscsag.plm.spi.interfaces.ECTRService;
import com.dscsag.plm.spi.interfaces.ai.tools.Tool;
import com.dscsag.plm.spi.interfaces.ai.tools.ToolParameter;
import com.dscsag.plm.spi.interfaces.ai.tools.ToolsComponent;

/**
 * @author wt
 * @since 1.0.0
 */
@Component (service = ToolsComponent.class)
public class ExampleMcpTool implements ToolsComponent
{
  @Reference ECTRService ectr;

  @Tool (value = "Echoes the supplied message unchanged. Use this tool to test the ECTR MCP integration; it does not read or modify PLM data.", name = "example_osgi_echo")
  public String call(@ToolParameter (value = "The message to echo unchanged") String message)
  {
    ectr.getPlmLogger().verbose("ExampleMcpTool echo called(" + message + ")");
    return message;
  }
}